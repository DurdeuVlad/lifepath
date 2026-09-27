package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.UnlockDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.unlock.UnlockService;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Admin {@code /lifepath unlock} tree (M9-4):
 * <ul>
 *   <li>{@code get <player>} — held unlock ids (the content the player may
 *       select; today: {@code selection:"unlocked"} species).</li>
 *   <li>{@code add <player> <unlock-id>} — fires the unlock definition:
 *       every content id it names lands in {@code unlocks[]}. This is the
 *       {@code type:admin} source.</li>
 *   <li>{@code remove <player> <unlock-id>} — revokes every content id the
 *       definition names. If two defs granted the same id, revoking one
 *       revokes the shared id (unlocks are a flat set by design).</li>
 * </ul>
 * The {@code unlocks[]} list itself holds content ids, not def ids — a def
 * is a grant rule, not a persisted token.
 */
public final class UnlockCommands {
	private UnlockCommands() {
	}

	private static boolean initialized;

	/** Contributes the {@code unlock} subcommand tree. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		var idArg = argument("id", IdentifierArgumentType.identifier())
				.suggests((ctx, builder) -> {
					LifepathContent.unlocks().all().keySet()
							.forEach(id -> builder.suggest(id.toString()));
					return builder.buildFuture();
				});
		LifepathCommands.register(literal("unlock")
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
										IdentifierArgumentType.getIdentifier(ctx, "id")))))));
	}

	private static int usage(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal(
				"usage: /lifepath unlock get|add|remove <player> [<unlock-id>]"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(ServerCommandSource source, ServerPlayerEntity target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		if (data.unlocks().isEmpty()) {
			source.sendFeedback(() -> Text.literal(
					target.getName().getString() + " has no unlocks"), false);
			return Command.SINGLE_SUCCESS;
		}
		source.sendFeedback(() -> Text.literal(target.getName().getString()
				+ " unlocks: " + data.unlocks()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int add(ServerCommandSource source, ServerPlayerEntity target, Identifier id) {
		UnlockDefinition def = LifepathContent.unlocks().get(id);
		if (def == null) {
			source.sendError(Text.literal("unknown unlock: " + id));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		int granted = 0;
		for (Identifier content : def.unlocks()) {
			if (UnlockService.grant(data, target, content)) {
				granted++;
			}
		}
		if (granted == 0) {
			source.sendError(Text.literal(target.getName().getString()
					+ " already holds everything " + id + " grants"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} granted unlock {} to {} ({})",
				source.getName(), id, target.getName().getString(), target.getUuid());
		int g = granted;
		source.sendFeedback(() -> Text.literal("granted " + id + " to "
				+ target.getName().getString() + " (" + g + " unlocks)"), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int remove(ServerCommandSource source, ServerPlayerEntity target, Identifier id) {
		UnlockDefinition def = LifepathContent.unlocks().get(id);
		if (def == null) {
			source.sendError(Text.literal("unknown unlock: " + id));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		int revoked = 0;
		for (Identifier content : def.unlocks()) {
			if (UnlockService.revoke(data, target, content)) {
				revoked++;
			}
		}
		if (revoked == 0) {
			source.sendError(Text.literal(target.getName().getString()
					+ " holds nothing " + id + " grants"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} revoked unlock {} from {} ({})",
				source.getName(), id, target.getName().getString(), target.getUuid());
		int r = revoked;
		source.sendFeedback(() -> Text.literal("revoked " + id + " from "
				+ target.getName().getString() + " (" + r + " unlocks)"), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
