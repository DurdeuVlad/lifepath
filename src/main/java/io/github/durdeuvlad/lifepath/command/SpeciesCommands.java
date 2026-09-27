package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * {@code /lifepath species} tree (M5-1; M9-4 added the enforced player path).
 *
 * <ul>
 *   <li>{@code get <player>} (admin) — show the current species and its key fields.</li>
 *   <li>{@code set <player> <id>} (admin) — set the species. Species effects are
 *       derived at eval time (ability/resource/aptitude refs), so a switch
 *       needs no cleanup; materialized resources keep ticking as owned
 *       character state. Admin override: {@code selection} rules are not
 *       enforced here.</li>
 *   <li>{@code choose <id>} (player-facing) — the enforced selection path:
 *       {@code selection} + {@code visibility} rules apply, and
 *       {@code unlocked} species require a held unlock id.</li>
 * </ul>
 */
public final class SpeciesCommands {
	private SpeciesCommands() {
	}

	private static boolean initialized;

	/** Contributes the {@code species} subcommand tree. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		LifepathCommands.register(literal("species")
				// Root is ungated so players can run `choose`; get/set carry the
				// admin requirement themselves (M9-4 — choose must be reachable
				// without permission 2).
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("get")
						.requires(src -> src.hasPermissionLevel(LifepathCommands.ADMIN_PERMISSION))
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> get(ctx.getSource(),
										EntityArgumentType.getPlayer(ctx, "player")))))
				.then(literal("set")
						.requires(src -> src.hasPermissionLevel(LifepathCommands.ADMIN_PERMISSION))
						.then(argument("player", EntityArgumentType.player())
								.then(argument("id", IdentifierArgumentType.identifier())
										.suggests((ctx, builder) -> {
											LifepathContent.species().all().keySet()
													.forEach(id -> builder.suggest(id.toString()));
											return builder.buildFuture();
										})
										.executes(ctx -> set(ctx.getSource(),
												EntityArgumentType.getPlayer(ctx, "player"),
												IdentifierArgumentType.getIdentifier(ctx, "id"))))))
				.then(literal("choose")
						.then(argument("id", IdentifierArgumentType.identifier())
								.suggests((ctx, builder) -> {
									// Player-facing picker: hidden species are
									// not offered (admins suggest all).
									boolean admin = ctx.getSource()
											.hasPermissionLevel(LifepathCommands.ADMIN_PERMISSION);
									LifepathContent.species().all().values().stream()
											.filter(def -> admin
													|| def.visibility() != SpeciesDefinition.Visibility.HIDDEN)
											.forEach(def -> builder.suggest(def.id().toString()));
									return builder.buildFuture();
								})
								.executes(ctx -> choose(ctx.getSource(),
										IdentifierArgumentType.getIdentifier(ctx, "id"))))));
	}

	/**
	 * Player-facing selection (M9-4): enforces {@code selection} + unlocks.
	 * {@code admin_only} species are denied for everyone — admins grant via
	 * {@code set} (logged admin path); {@code unlocked} requires the id in the
	 * player's {@code unlocks[]} (granted by the unlock-source framework).
	 */
	private static int choose(ServerCommandSource source, Identifier speciesId) {
		ServerPlayerEntity player;
		try {
			player = source.getPlayerOrThrow();
		} catch (Exception e) {
			source.sendError(Text.literal("only a player can choose a species"));
			return 0;
		}
		SpeciesDefinition def = LifepathContent.species().get(speciesId);
		if (def == null) {
			source.sendError(Text.literal("unknown species: " + speciesId));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (!chooseAllowed(data, def)) {
			source.sendError(Text.literal(def.selection() == SpeciesDefinition.Selection.UNLOCKED
					? "species " + speciesId + " requires an unlock you don't hold"
					: "species " + speciesId + " is not selectable"));
			return 0;
		}
		data.setSpeciesId(speciesId);
		CharacterManager.changed(player);
		source.sendFeedback(() -> Text.literal("species set to "
				+ def.displayName()), false);
		// Same consequence line `set` delivers — the player learns what the
		// pick means (M6-4 zero-confusion rule).
		def.description().ifPresent(d ->
				player.sendMessage(Text.literal(d), false));
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * The selection policy {@code choose} enforces — pulled out so tests cover
	 * the rule without a live command source. {@code open} always selects;
	 * {@code unlocked} requires the species id in {@code unlocks[]};
	 * {@code admin_only} is never player-choosable (admins use {@code set}).
	 */
	public static boolean chooseAllowed(PlayerCharacterData data, SpeciesDefinition def) {
		return switch (def.selection()) {
			case OPEN -> true;
			case UNLOCKED -> io.github.durdeuvlad.lifepath.unlock.UnlockService
					.isUnlocked(data, def.id());
			case ADMIN_ONLY -> false;
		};
	}

	private static int usage(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal(
				"usage: /lifepath species choose <id> | species get|set <player> <id> (admin)"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(ServerCommandSource source, ServerPlayerEntity target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		Identifier speciesId = data.speciesId();
		if (speciesId == null) {
			source.sendFeedback(() -> Text.literal(
					target.getName().getString() + " has no species"), false);
			return Command.SINGLE_SUCCESS;
		}
		SpeciesDefinition def = LifepathContent.species().get(speciesId);
		source.sendFeedback(() -> Text.literal(target.getName().getString()
				+ " species: " + speciesId
				+ (def == null ? " (definition missing — effects inactive)"
						: " \"" + def.displayName() + "\""
								+ " passive=" + def.passiveAbilities().size()
								+ " active=" + def.activeAbilities().size()
								+ " resources=" + def.resources().size()
								+ def.description().map(d -> " — " + d).orElse(""))), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int set(ServerCommandSource source, ServerPlayerEntity target, Identifier speciesId) {
		if (!LifepathContent.species().contains(speciesId)) {
			source.sendError(Text.literal("unknown species: " + speciesId));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		data.setSpeciesId(speciesId);
		CharacterManager.markDirty(target);
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		LifepathMod.LOGGER.info("admin action: {} set {} ({}) species to {}",
				source.getName(), target.getName().getString(), target.getUuid(), speciesId);
		source.sendFeedback(() -> Text.literal("set " + target.getName().getString()
				+ " species to " + speciesId), true);
		// M6-4 consequence preview: species_assigned carries only the name —
		// the recipient also gets the identity description line.
		var def = LifepathContent.species().get(speciesId);
		if (def != null && def.description().isPresent()) {
			target.sendMessage(Text.literal(def.description().get()), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
