package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.specialization.SpecializationService;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Admin {@code /lifepath specialization} tree (M3-2). The player-facing
 * selection UX is M6 — this is the test/admin path.
 *
 * <ul>
 *   <li>{@code get <player>} — show the current specialization and its key fields.</li>
 *   <li>{@code set <player> <id>} — apply the spec: sets the id, raises
 *       starting levels (never lowers), applies aptitude overrides and
 *       protected floors (all raise-only). There is no clear/un-apply path —
 *       baked grants are irrevocable short of {@code character reset}.</li>
 * </ul>
 */
public final class SpecializationCommands {
	private SpecializationCommands() {
	}

	private static boolean initialized;

	/** Contributes the {@code specialization} subcommand tree. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		LifepathCommands.register(literal("specialization")
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
											LifepathContent.specializations().all().keySet()
													.forEach(id -> builder.suggest(id.toString()));
											return builder.buildFuture();
										})
										.executes(ctx -> set(ctx.getSource(),
												EntityArgumentType.getPlayer(ctx, "player"),
												IdentifierArgumentType.getIdentifier(ctx, "id")))))));
	}

	private static int usage(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal(
				"usage: /lifepath specialization get <player> | set <player> <id>"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(ServerCommandSource source, ServerPlayerEntity target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		Identifier specId = data.specializationId();
		if (specId == null) {
			source.sendFeedback(() -> Text.literal(
					target.getName().getString() + " has no specialization"), false);
			return Command.SINGLE_SUCCESS;
		}
		SpecializationDefinition def = LifepathContent.specializations().get(specId);
		source.sendFeedback(() -> Text.literal(target.getName().getString()
				+ " specialization: " + specId
				+ (def == null ? " (definition missing — effects inactive)"
						: " \"" + def.displayName() + "\""
								+ " starts=" + def.startingSkills().size()
								+ " aptitudes=" + def.aptitudes().size()
								+ " floors=" + def.protectedFloors().size())), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int set(ServerCommandSource source, ServerPlayerEntity target, Identifier specId) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		SpecializationService.ApplyResult result = SpecializationService.apply(data, specId);
		if (result == SpecializationService.ApplyResult.UNKNOWN_SPEC) {
			source.sendError(Text.literal("unknown specialization: " + specId));
			return 0;
		}
		CharacterManager.markDirty(target);
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		LifepathMod.LOGGER.info("admin action: {} set {} ({}) specialization to {}",
				source.getName(), target.getName().getString(), target.getUuid(), specId);
		source.sendFeedback(() -> Text.literal("set " + target.getName().getString()
				+ " specialization to " + specId), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
