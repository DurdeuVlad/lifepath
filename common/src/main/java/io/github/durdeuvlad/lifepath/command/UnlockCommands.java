package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.UnlockDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.unlock.UnlockService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

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
		var idArg = argument("id", ResourceLocationArgument.id())
				.suggests((ctx, builder) -> {
					LifepathContent.unlocks().all().keySet()
							.forEach(id -> builder.suggest(id.toString()));
					return builder.buildFuture();
				});
		LifepathCommands.register(literal("unlock")
				.requires(src -> src.hasPermission(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("get")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> get(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("add")
						.then(argument("player", EntityArgument.player())
								.then(idArg.executes(ctx -> add(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player"),
										ResourceLocationArgument.getId(ctx, "id"))))))
				.then(literal("remove")
						.then(argument("player", EntityArgument.player())
								.then(idArg.executes(ctx -> remove(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player"),
										ResourceLocationArgument.getId(ctx, "id")))))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"usage: /lifepath unlock get|add|remove <player> [<unlock-id>]"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		if (data.unlocks().isEmpty()) {
			source.sendSuccess(() -> Component.literal(
					target.getName().getString() + " has no unlocks"), false);
			return Command.SINGLE_SUCCESS;
		}
		source.sendSuccess(() -> Component.literal(target.getName().getString()
				+ " unlocks: " + data.unlocks()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int add(CommandSourceStack source, ServerPlayer target, ResourceLocation id) {
		UnlockDefinition def = LifepathContent.unlocks().get(id);
		if (def == null) {
			source.sendFailure(Component.literal("unknown unlock: " + id));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		int granted = 0;
		for (ResourceLocation content : def.unlocks()) {
			if (UnlockService.grant(data, target, content)) {
				granted++;
			}
		}
		if (granted == 0) {
			source.sendFailure(Component.literal(target.getName().getString()
					+ " already holds everything " + id + " grants"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} granted unlock {} to {} ({})",
				source.getTextName(), id, target.getName().getString(), target.getUUID());
		int g = granted;
		source.sendSuccess(() -> Component.literal("granted " + id + " to "
				+ target.getName().getString() + " (" + g + " unlocks)"), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int remove(CommandSourceStack source, ServerPlayer target, ResourceLocation id) {
		UnlockDefinition def = LifepathContent.unlocks().get(id);
		if (def == null) {
			source.sendFailure(Component.literal("unknown unlock: " + id));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		int revoked = 0;
		for (ResourceLocation content : def.unlocks()) {
			if (UnlockService.revoke(data, target, content)) {
				revoked++;
			}
		}
		if (revoked == 0) {
			source.sendFailure(Component.literal(target.getName().getString()
					+ " holds nothing " + id + " grants"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} revoked unlock {} from {} ({})",
				source.getTextName(), id, target.getName().getString(), target.getUUID());
		int r = revoked;
		source.sendSuccess(() -> Component.literal("revoked " + id + " from "
				+ target.getName().getString() + " (" + r + " unlocks)"), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
