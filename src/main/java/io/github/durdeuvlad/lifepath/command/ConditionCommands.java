package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.condition.ConditionService;
import io.github.durdeuvlad.lifepath.condition.ConditionState;
import io.github.durdeuvlad.lifepath.content.ConditionDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Admin {@code /lifepath condition} tree (M9-1, GAMEDESIGN §22):
 * <ul>
 *   <li>{@code get <player>} — held conditions with stage + display name.</li>
 *   <li>{@code add <player> <id>} — acquire at stage 0 (the {@code admin}
 *       acquisition channel).</li>
 *   <li>{@code remove <player> <id>} — cure unconditionally (admin cures are
 *       always allowed; a def need not declare one).</li>
 *   <li>{@code stage <player> <id> <n>} — pin stage for testing.</li>
 * </ul>
 * Effective state changes are dirty+synced; the recipient's notice rides the
 * M6-4 condition add/remove feedback diff emitted by the sync.
 */
public final class ConditionCommands {
	private ConditionCommands() {
	}

	private static boolean initialized;

	/** Contributes the {@code condition} subcommand tree. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		var idArg = argument("id", IdentifierArgumentType.identifier())
				.suggests((ctx, builder) -> {
					LifepathContent.conditions().all().keySet()
							.forEach(id -> builder.suggest(id.toString()));
					return builder.buildFuture();
				});
		LifepathCommands.register(literal("condition")
				.requires(src -> src.hasPermissionLevel(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("get")
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> get(ctx.getSource(),
										EntityArgumentType.getPlayer(ctx, "player")))))
				.then(literal("add")
						.then(argument("player", EntityArgumentType.player())
								.then(idArg.executes(ctx -> add(ctx.getSource(),
										EntityArgumentType.getPlayer(ctx, "player"),
										IdentifierArgumentType.getIdentifier(ctx, "id"))))))
				.then(literal("remove")
						.then(argument("player", EntityArgumentType.player())
								.then(idArg.executes(ctx -> remove(ctx.getSource(),
										EntityArgumentType.getPlayer(ctx, "player"),
										IdentifierArgumentType.getIdentifier(ctx, "id"))))))
				.then(literal("stage")
						.then(argument("player", EntityArgumentType.player())
								.then(idArg.then(argument("n", IntegerArgumentType.integer(0))
										.executes(ctx -> stage(ctx.getSource(),
												EntityArgumentType.getPlayer(ctx, "player"),
												IdentifierArgumentType.getIdentifier(ctx, "id"),
												IntegerArgumentType.getInteger(ctx, "n"))))))));
	}

	private static int usage(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal(
				"usage: /lifepath condition get|add|remove|stage <player> [<id> [<n>]]"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(ServerCommandSource source, ServerPlayerEntity target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		if (data.conditions().isEmpty()) {
			source.sendFeedback(() -> Text.literal(
					target.getName().getString() + " has no conditions"), false);
			return Command.SINGLE_SUCCESS;
		}
		for (Identifier id : data.conditions()) {
			ConditionDefinition def = LifepathContent.conditions().get(id);
			ConditionState st = data.conditionState(id);
			String stage = def == null || st == null ? "?" :
					def.stages().get(Math.min(st.stage(), def.stageCount() - 1)).id()
							+ " (" + st.stage() + "/" + def.stageCount() + ")";
			source.sendFeedback(() -> Text.literal(target.getName().getString()
					+ " condition: " + id + " \"" + (def == null ? "missing" : def.displayName())
					+ "\" stage " + stage), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int add(ServerCommandSource source, ServerPlayerEntity target, Identifier id) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		boolean ok = ConditionService.acquire(data, target, id, System.currentTimeMillis());
		if (!ok) {
			source.sendError(Text.literal("cannot add " + id
					+ " (unknown definition or already held)"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} added condition {} to {} ({})",
				source.getName(), id, target.getName().getString(), target.getUuid());
		source.sendFeedback(() -> Text.literal("added condition " + id + " to "
				+ target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int remove(ServerCommandSource source, ServerPlayerEntity target, Identifier id) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		boolean ok = ConditionService.cure(data, target, id);
		if (!ok) {
			source.sendError(Text.literal("cannot remove " + id
					+ " (unknown definition or not held)"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} removed condition {} from {} ({})",
				source.getName(), id, target.getName().getString(), target.getUuid());
		source.sendFeedback(() -> Text.literal("removed condition " + id + " from "
				+ target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int stage(ServerCommandSource source, ServerPlayerEntity target,
			Identifier id, int n) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		ConditionDefinition def = LifepathContent.conditions().get(id);
		ConditionState st = data.conditionState(id);
		if (def == null || st == null) {
			source.sendError(Text.literal(id + " is not held by " + target.getName().getString()));
			return 0;
		}
		int clamped = Math.max(0, Math.min(n, def.stageCount() - 1));
		data.putCondition(id, new ConditionState(clamped, System.currentTimeMillis(), 0));
		CharacterManager.changed(target);
		LifepathMod.LOGGER.info("admin action: {} set {} condition {} stage to {}",
				source.getName(), target.getName().getString(), id, clamped);
		int c = clamped;
		source.sendFeedback(() -> Text.literal("set " + target.getName().getString()
				+ " condition " + id + " to stage " + c), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
