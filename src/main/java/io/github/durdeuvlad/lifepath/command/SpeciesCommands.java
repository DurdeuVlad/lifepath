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
 * Admin {@code /lifepath species} tree (M5-1). The player-facing selection UX
 * is M6 — this is the test/admin path that makes species playable end-to-end
 * before then (same role {@code /lifepath specialization} plays for specs).
 *
 * <ul>
 *   <li>{@code get <player>} — show the current species and its key fields.</li>
 *   <li>{@code set <player> <id>} — set the species. Species effects are
 *       derived at eval time (ability/resource/aptitude refs), so a switch
 *       needs no cleanup; materialized resources keep ticking as owned
 *       character state. Admin override: {@code selection} rules are not
 *       enforced here.</li>
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
				.requires(src -> src.hasPermissionLevel(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("get")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> get(ctx.getSource(),
										EntityArgumentType.getPlayer(ctx, "player")))))
				.then(literal("set")
						.then(argument("player", EntityArgumentType.player())
								.then(argument("id", IdentifierArgumentType.identifier())
										.suggests((ctx, builder) -> {
											LifepathContent.species().all().keySet()
													.forEach(id -> builder.suggest(id.toString()));
											return builder.buildFuture();
										})
										.executes(ctx -> set(ctx.getSource(),
												EntityArgumentType.getPlayer(ctx, "player"),
												IdentifierArgumentType.getIdentifier(ctx, "id")))))));
	}

	private static int usage(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal(
				"usage: /lifepath species get <player> | set <player> <id>"), false);
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
								+ " resources=" + def.resources().size())), false);
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
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
