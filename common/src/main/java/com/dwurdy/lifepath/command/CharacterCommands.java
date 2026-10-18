package com.dwurdy.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.character.persistence.PendingResetsData;
import com.dwurdy.lifepath.morph.MorphService;
import com.dwurdy.lifepath.selection.SelectionService;
import com.dwurdy.lifepath.skill.SkillProgress;
import com.dwurdy.lifepath.species.SpeciesScaleService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /lifepath character} — admin tooling for character inspection and
 * reset (permission level 2+, works from the server console).
 *
 * <ul>
 *   <li>{@code inspect <player>} prints every persisted field of the target's
 *       character data — the same model the cache holds, so output is
 *       authoritative.
 *   <li>{@code reset <player> confirm} restores fresh defaults, persists
 *       immediately, re-syncs the client's mirror, and logs who reset whom.
 *       The literal {@code confirm} is required — bare {@code reset} refuses.
 *   <li>{@code resetall confirm} does the same for every ONLINE player, then
 *       marks every offline UUID that has a {@code playerdata/<uuid>.dat} in
 *       {@link PendingResetsData} — {@code CharacterManager.initializeCharacter}
 *       consumes the marker on their next login, so offline players are reset
 *       and re-pick on return. The literal {@code confirm} is required.
 * </ul>
 *
 * <p>Per-player inspection/editing operates on ONLINE players only
 * (entity-selector argument resolves against connected players); {@code
 * resetall} is the exception — it reaches offline players via pending markers.
 */
public final class CharacterCommands {
	private static boolean initialized;

	private CharacterCommands() {
	}

	/** Contributes the {@code character} subcommand tree. Call during mod init; idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		LifepathCommands.register(literal("character")
				.requires(src -> src.hasPermission(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("inspect")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> inspect(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("reset")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> refuseReset(ctx.getSource()))
								.then(literal("confirm")
										.executes(ctx -> reset(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "player"))))))
				.then(literal("resetall")
						.executes(ctx -> refuseResetAll(ctx.getSource()))
						.then(literal("confirm")
								.executes(ctx -> resetAll(ctx.getSource())))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"usage: /lifepath character inspect <player> | reset <player> confirm"
						+ " | resetall confirm"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int inspect(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		// Lazy decay trigger (M3-3): inspection shows current, decayed values.
		if (com.dwurdy.lifepath.skill.SkillDecayService.applyLazyAll(
				data, System.currentTimeMillis()) > 0) {
			CharacterManager.markDirty(target);
			CharacterManager.syncCharacter(target);
		}
		source.sendSuccess(() -> Component.literal("Lifepath character: "
				+ target.getName().getString() + " (" + target.getUUID() + ")"), false);
		for (Component line : describe(data, System.currentTimeMillis())) {
			source.sendSuccess(() -> line, false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int refuseReset(CommandSourceStack source) {
		source.sendFailure(Component.literal(
				"refusing: reset destroys all character data — append the literal 'confirm'"));
		return 0;
	}

	private static int reset(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		// Strip live morph/scale BEFORE the data wipe: clearActiveMorph is a
		// no-op once morph is null, so it must run while the state is still
		// readable (same ordering selectSpecies/species set use). Scale is
		// re-resolved after the wipe — null species → base 1.0.
		MorphService.clearActiveMorph(target, data);
		data.reset();
		SpeciesScaleService.apply(target);
		// markDirty covers the edge where saveCharacter no-ops (selector resolved a
		// zombie session during a duplicate login): the entry's real owner then
		// picks it up at the next periodic/disconnect flush.
		CharacterManager.markDirty(target);
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		// The catalog encodes per-player ALREADY_CHOSEN state — without this
		// push the reset player's pickers stay locked client-side until relog
		// (same recipe resetall and specialization clear use).
		SelectionService.pushCatalog(target);
		LifepathMod.LOGGER.info("admin action: {} reset character data for {} ({})",
				source.getTextName(), target.getName().getString(), target.getUUID());
		source.sendSuccess(() -> Component.literal(
				"reset character data for " + target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int refuseResetAll(CommandSourceStack source) {
		source.sendFailure(Component.literal(
				"refusing: resetall destroys EVERY player's character data"
						+ " — append the literal 'confirm'"));
		return 0;
	}

	/**
	 * The server-wide wipe. Online players are reset live (demorph → reset →
	 * scale → dirty → save → sync → catalog re-push, same recipe as
	 * {@link #reset} plus the picker re-arm). Offline players can't be
	 * touched — their entity is unloaded — so each UUID with a
	 * {@code playerdata/<uuid>.dat} file is marked in
	 * {@link PendingResetsData} instead;
	 * {@code CharacterManager.initializeCharacter} consumes the marker on
	 * their next login (ahead of the join catalog push, which then re-arms
	 * the pickers). Online UUIDs are explicitly consumed out of the set —
	 * their live reset already happened; leaving a marker would wipe them
	 * AGAIN at their next relog.
	 */
	private static int resetAll(CommandSourceStack source) {
		MinecraftServer server = source.getServer();
		List<ServerPlayer> online = server.getPlayerList().getPlayers();
		Set<UUID> onlineIds = new HashSet<>();
		for (ServerPlayer player : online) {
			onlineIds.add(player.getUUID());
			PlayerCharacterData data = CharacterManager.getCharacter(player);
			MorphService.clearActiveMorph(player, data);
			data.reset();
			SpeciesScaleService.apply(player);
			CharacterManager.markDirty(player);
			CharacterManager.saveCharacter(player);
			CharacterManager.syncCharacter(player);
			SelectionService.pushCatalog(player);
		}
		PendingResetsData pending = PendingResetsData.get(server);
		for (UUID uuid : onlineIds) {
			pending.consume(uuid);
		}
		int offlineMarked = 0;
		for (UUID uuid : knownPlayerUuids(server)) {
			if (!onlineIds.contains(uuid)) {
				pending.add(uuid);
				offlineMarked++;
			}
		}
		// Persist the marker set now — the normal autosave window would make
		// the offline half of a just-confirmed wipe crash-fragile.
		server.overworld().getDataStorage().save();
		LifepathMod.LOGGER.info(
				"admin action: {} reset ALL character data — {} online reset now,"
						+ " {} offline marked pending",
				source.getTextName(), online.size(), offlineMarked);
		int onlineCount = online.size();
		int markedCount = offlineMarked;
		source.sendSuccess(() -> Component.literal("reset character data for "
				+ onlineCount + " online player(s); " + markedCount
				+ " offline player(s) marked for reset on next login"), true);
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * UUIDs of every player who has ever joined: the filenames of
	 * {@code playerdata/<uuid>.dat}. No vanilla API enumerates offline
	 * players (the profile cache looks up by name/id only) — the file set
	 * IS the authoritative roster. Missing/unreadable directory → empty set.
	 */
	private static Set<UUID> knownPlayerUuids(MinecraftServer server) {
		Set<UUID> uuids = new HashSet<>();
		Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
		try (var stream = Files.list(dir)) {
			stream.map(path -> path.getFileName().toString())
					.filter(name -> name.endsWith(".dat"))
					.forEach(name -> {
						try {
							uuids.add(UUID.fromString(
									name.substring(0, name.length() - ".dat".length())));
						} catch (IllegalArgumentException e) {
							// Not a <uuid>.dat file (e.g. stray junk) — skip.
						}
					});
		} catch (IOException e) {
			LifepathMod.LOGGER.warn(
					"resetall: could not enumerate playerdata dir {}: {}", dir, e.toString());
		}
		return uuids;
	}

	/**
	 * Readable multi-line dump of every persisted field — kept pure (model +
	 * clock in, lines out) so tests assert exact output.
	 */
	static List<Component> describe(PlayerCharacterData data, long nowMillis) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("  species: " + orNone(data.speciesId())
				+ "  specialization: " + orNone(data.specializationId())
				+ "  data_version: " + data.dataVersion()));
		if (data.skills().isEmpty()) {
			lines.add(Component.literal("  skills: <none>"));
		} else {
			for (Map.Entry<ResourceLocation, SkillProgress> skill : data.skills().entrySet()) {
				SkillProgress p = skill.getValue();
				lines.add(Component.literal("  skill " + skill.getKey()
						+ "  level=" + p.level()
						+ "  xp=" + p.xp()
						+ "  highest=" + p.highestLevel()
						+ "  floor=" + p.protectedFloor()
						+ "  aptitude=" + p.aptitude()
						+ "  last_use=" + formatUse(p.lastMeaningfulUse())));
			}
		}
		lines.add(Component.literal("  traits: " + ids(data.traits())));
		lines.add(Component.literal("  conditions: " + ids(data.conditions())));
		lines.add(Component.literal("  attunements: " + ids(data.attunements())));
		lines.add(Component.literal("  unlocks: " + ids(data.unlocks())));
		if (data.resources().isEmpty()) {
			lines.add(Component.literal("  resources: <none>"));
		} else {
			for (Map.Entry<ResourceLocation, PlayerCharacterData.ResourceState> res : data.resources().entrySet()) {
				PlayerCharacterData.ResourceState s = res.getValue();
				lines.add(Component.literal("  resource " + res.getKey()
						+ "  " + s.current() + "/" + s.min() + "-" + s.max()));
			}
		}
		// Passive-schedule markers are engine bookkeeping — not shown as
		// cooldowns in admin output (M4-4).
		var visibleCooldowns = data.cooldowns().entrySet().stream()
				.filter(e -> !com.dwurdy.lifepath.ability.CooldownService
						.isScheduleKey(e.getKey()))
				.toList();
		if (visibleCooldowns.isEmpty()) {
			lines.add(Component.literal("  cooldowns: <none>"));
		} else {
			for (Map.Entry<ResourceLocation, Long> cd : visibleCooldowns) {
				long remaining = cd.getValue() - nowMillis;
				lines.add(Component.literal("  cooldown " + cd.getKey()
						+ (remaining <= 0
								? "  expired " + (-remaining) + "ms ago"
								: "  expires in " + remaining + "ms")));
			}
		}
		// M3-4 instrumentation: active repetition signatures with their
		// in-window counts — the balance-tuning surface for diminishing returns.
		Map<String, List<Long>> sigs = data.actionSignatures();
		if (sigs.isEmpty()) {
			lines.add(Component.literal("  action signatures: <none>"));
		} else {
			long cutoff = nowMillis
					- com.dwurdy.lifepath.skill.DiminishingReturns.windowMs();
			for (Map.Entry<String, List<Long>> sig : sigs.entrySet()) {
				long active = sig.getValue().stream().filter(t -> t > cutoff).count();
				if (active > 0) {
					lines.add(Component.literal("  sig " + sig.getKey() + "  count=" + active
							+ "  mult=" + com.dwurdy.lifepath.skill
									.DiminishingReturns.multiplierFor((int) active)));
				}
			}
		}
		return List.copyOf(lines);
	}

	private static String orNone(@Nullable ResourceLocation id) {
		return id == null ? "<none>" : id.toString();
	}

	/** Test hook: clears the init flag so {@link #init()} re-registers. Not for production use. */
	static void resetForTests() {
		initialized = false;
	}

	private static String ids(List<ResourceLocation> list) {
		return list.isEmpty() ? "<none>" : list.toString();
	}

	private static String formatUse(long epochMillis) {
		return epochMillis <= 0 ? "<never>" : Instant.ofEpochMilli(epochMillis).toString();
	}
}
