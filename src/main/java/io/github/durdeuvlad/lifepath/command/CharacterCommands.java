package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
 * </ul>
 *
 * <p>v1 operates on ONLINE players only (entity-selector argument resolves
 * against connected players); offline editing is intentionally out of scope.
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
												EntityArgument.getPlayer(ctx, "player")))))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"usage: /lifepath character inspect <player> | reset <player> confirm"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int inspect(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		// Lazy decay trigger (M3-3): inspection shows current, decayed values.
		if (io.github.durdeuvlad.lifepath.skill.SkillDecayService.applyLazyAll(
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
		data.reset();
		// markDirty covers the edge where saveCharacter no-ops (selector resolved a
		// zombie session during a duplicate login): the entry's real owner then
		// picks it up at the next periodic/disconnect flush.
		CharacterManager.markDirty(target);
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		LifepathMod.LOGGER.info("admin action: {} reset character data for {} ({})",
				source.getTextName(), target.getName().getString(), target.getUUID());
		source.sendSuccess(() -> Component.literal(
				"reset character data for " + target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
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
				.filter(e -> !io.github.durdeuvlad.lifepath.ability.CooldownService
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
					- io.github.durdeuvlad.lifepath.skill.DiminishingReturns.windowMs();
			for (Map.Entry<String, List<Long>> sig : sigs.entrySet()) {
				long active = sig.getValue().stream().filter(t -> t > cutoff).count();
				if (active > 0) {
					lines.add(Component.literal("  sig " + sig.getKey() + "  count=" + active
							+ "  mult=" + io.github.durdeuvlad.lifepath.skill
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
