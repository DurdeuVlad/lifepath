package com.dwurdy.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.specialization.SpecializationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

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
				.requires(src -> src.hasPermission(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("get")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> get(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("set")
						.then(argument("player", EntityArgument.player())
								.then(argument("id", ResourceLocationArgument.id())
										.suggests((ctx, builder) -> {
											LifepathContent.specializations().all().keySet()
													.forEach(id -> builder.suggest(id.toString()));
											return builder.buildFuture();
										})
										.executes(ctx -> set(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "player"),
												ResourceLocationArgument.getId(ctx, "id")))))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"usage: /lifepath specialization get <player> | set <player> <id>"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		ResourceLocation specId = data.specializationId();
		if (specId == null) {
			source.sendSuccess(() -> Component.literal(
					target.getName().getString() + " has no specialization"), false);
			return Command.SINGLE_SUCCESS;
		}
		SpecializationDefinition def = LifepathContent.specializations().get(specId);
		source.sendSuccess(() -> Component.literal(target.getName().getString()
				+ " specialization: " + specId
				+ (def == null ? " (definition missing — effects inactive)"
						: " \"" + def.displayName() + "\""
								+ " starts=" + def.startingSkills().size()
								+ " aptitudes=" + def.aptitudes().size()
								+ " floors=" + def.protectedFloors().size())), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int set(CommandSourceStack source, ServerPlayer target, ResourceLocation specId) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		SpecializationService.ApplyResult result = SpecializationService.apply(data, specId);
		if (result == SpecializationService.ApplyResult.UNKNOWN_SPEC) {
			source.sendFailure(Component.literal("unknown specialization: " + specId));
			return 0;
		}
		CharacterManager.markDirty(target);
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		LifepathMod.LOGGER.info("admin action: {} set {} ({}) specialization to {}",
				source.getTextName(), target.getName().getString(), target.getUUID(), specId);
		// The recipient's notice (name + non-lockout line) rides the M6-4
		// spec_assigned feedback emitted by the sync diff above — no
		// duplicate literal here.
		source.sendSuccess(() -> Component.literal("set " + target.getName().getString()
				+ " specialization to " + specId), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
