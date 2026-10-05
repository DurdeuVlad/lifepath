package com.dwurdy.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.attunement.AttunementService;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AttunementDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin {@code /lifepath attunement} tree (M9-2, issue #42):
 * {@code get|add|remove <player> [<attunement>]} — permission 2, logged,
 * recipient feedback rides the M6-4 attunement add/remove diff.
 */
public final class AttunementCommands {
	private AttunementCommands() {
	}

	private static boolean initialized;

	/** Contributes the {@code attunement} subcommand tree. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		var idArg = argument("id", ResourceLocationArgument.id())
				.suggests((ctx, builder) -> {
					LifepathContent.attunements().all().keySet()
							.forEach(id -> builder.suggest(id.toString()));
					return builder.buildFuture();
				});
		LifepathCommands.register(literal("attunement")
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
				"usage: /lifepath attunement get|add|remove <player> [<id>]"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		if (data.attunements().isEmpty()) {
			source.sendSuccess(() -> Component.literal(
					target.getName().getString() + " has no attunements"), false);
			return Command.SINGLE_SUCCESS;
		}
		for (ResourceLocation id : data.attunements()) {
			AttunementDefinition def = LifepathContent.attunements().get(id);
			source.sendSuccess(() -> Component.literal(target.getName().getString()
					+ " attunement: " + id + " \""
					+ (def == null ? "missing" : def.displayName()) + "\""), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int add(CommandSourceStack source, ServerPlayer target, ResourceLocation id) {
		boolean ok = AttunementService.attune(CharacterManager.getCharacter(target), target, id);
		if (!ok) {
			source.sendFailure(Component.literal("cannot add " + id
					+ " (unknown definition or already held)"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} added attunement {} to {} ({})",
				source.getTextName(), id, target.getName().getString(), target.getUUID());
		source.sendSuccess(() -> Component.literal("added attunement " + id + " to "
				+ target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int remove(CommandSourceStack source, ServerPlayer target, ResourceLocation id) {
		boolean ok = AttunementService.unattune(CharacterManager.getCharacter(target), target, id);
		if (!ok) {
			source.sendFailure(Component.literal("cannot remove " + id
					+ " (unknown definition or not held)"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} removed attunement {} from {} ({})",
				source.getTextName(), id, target.getName().getString(), target.getUUID());
		source.sendSuccess(() -> Component.literal("removed attunement " + id + " from "
				+ target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
