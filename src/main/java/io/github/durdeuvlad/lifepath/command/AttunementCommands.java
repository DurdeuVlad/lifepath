package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.attunement.AttunementService;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AttunementDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
		var idArg = argument("id", IdentifierArgumentType.identifier())
				.suggests((ctx, builder) -> {
					LifepathContent.attunements().all().keySet()
							.forEach(id -> builder.suggest(id.toString()));
					return builder.buildFuture();
				});
		LifepathCommands.register(literal("attunement")
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
				"usage: /lifepath attunement get|add|remove <player> [<id>]"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(ServerCommandSource source, ServerPlayerEntity target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		if (data.attunements().isEmpty()) {
			source.sendFeedback(() -> Text.literal(
					target.getName().getString() + " has no attunements"), false);
			return Command.SINGLE_SUCCESS;
		}
		for (Identifier id : data.attunements()) {
			AttunementDefinition def = LifepathContent.attunements().get(id);
			source.sendFeedback(() -> Text.literal(target.getName().getString()
					+ " attunement: " + id + " \""
					+ (def == null ? "missing" : def.displayName()) + "\""), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int add(ServerCommandSource source, ServerPlayerEntity target, Identifier id) {
		boolean ok = AttunementService.attune(CharacterManager.getCharacter(target), target, id);
		if (!ok) {
			source.sendError(Text.literal("cannot add " + id
					+ " (unknown definition or already held)"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} added attunement {} to {} ({})",
				source.getName(), id, target.getName().getString(), target.getUuid());
		source.sendFeedback(() -> Text.literal("added attunement " + id + " to "
				+ target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int remove(ServerCommandSource source, ServerPlayerEntity target, Identifier id) {
		boolean ok = AttunementService.unattune(CharacterManager.getCharacter(target), target, id);
		if (!ok) {
			source.sendError(Text.literal("cannot remove " + id
					+ " (unknown definition or not held)"));
			return 0;
		}
		LifepathMod.LOGGER.info("admin action: {} removed attunement {} from {} ({})",
				source.getName(), id, target.getName().getString(), target.getUuid());
		source.sendFeedback(() -> Text.literal("removed attunement " + id + " from "
				+ target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
