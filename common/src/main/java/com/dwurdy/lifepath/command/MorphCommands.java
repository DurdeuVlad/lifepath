package com.dwurdy.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.morph.MorphService;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.selection.SelectionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lifepath morph} tree (morph feature M-2): the admin override for
 * the one-time form pick. Players pick in the selection screen's form step;
 * these commands exist to fix stuck state — an anima whose pick never
 * landed, or a forced re-pick the GUI can't do on multiplayer.
 *
 * <ul>
 *   <li>{@code get <player>} (admin) — show the picked form and whether the
 *       morph is active.</li>
 *   <li>{@code set <player> <form>} (admin) — set the form. The target's
 *       species must carry a {@code morph_toggle} ability (the same rule
 *       the picker enforces); an active morph is dropped first so the old
 *       shape's stats can't linger.</li>
 *   <li>{@code clear <player>} (admin) — drop the pick entirely; the target
 *       can pick again through the normal GUI path.</li>
 * </ul>
 */
public final class MorphCommands {
	private MorphCommands() {
	}

	private static boolean initialized;

	/** Contributes the {@code morph} subcommand tree. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		LifepathCommands.register(literal("morph")
				.requires(src -> src.hasPermission(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()))
				.then(literal("get")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> get(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("set")
						.then(argument("player", EntityArgument.player())
								.then(argument("form", ResourceLocationArgument.id())
										.suggests((ctx, builder) -> {
											LifepathContent.morphForms().all().keySet()
													.forEach(id -> builder.suggest(id.toString()));
											return builder.buildFuture();
										})
										.executes(ctx -> set(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "player"),
												ResourceLocationArgument.getId(ctx, "form"))))))
				.then(literal("clear")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> clear(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player"))))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"usage: /lifepath morph get|set|clear <player> [form]"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		var morph = data.morph();
		if (morph == null) {
			source.sendSuccess(() -> Component.literal(
					target.getName().getString() + " has no morph form"), false);
			return Command.SINGLE_SUCCESS;
		}
		var def = LifepathContent.morphForms().get(morph.formId());
		source.sendSuccess(() -> Component.literal(target.getName().getString()
				+ " morph: " + morph.formId()
				+ (def == null ? " (definition missing)"
						: " \"" + def.displayName() + "\"")
				+ (morph.active() ? " [active]" : "")), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int set(CommandSourceStack source, ServerPlayer target,
			ResourceLocation formId) {
		if (!LifepathContent.morphForms().contains(formId)) {
			source.sendFailure(Component.literal("unknown morph form: " + formId));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		SpeciesDefinition species = data.speciesId() == null ? null
				: LifepathContent.species().get(data.speciesId());
		if (species == null || !MorphService.speciesHasMorphToggle(species)) {
			source.sendFailure(Component.literal(target.getName().getString()
					+ "'s species cannot morph"));
			return 0;
		}
		MorphService.clearActiveMorph(target, data);
		data.setMorph(new PlayerCharacterData.MorphState(formId, false,
				System.currentTimeMillis()));
		persist(source, target);
		LifepathMod.LOGGER.info("admin action: {} set {} ({}) morph form to {}",
				source.getTextName(), target.getName().getString(), target.getUUID(), formId);
		source.sendSuccess(() -> Component.literal("set " + target.getName().getString()
				+ " morph form to " + formId), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int clear(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		if (data.morph() == null) {
			source.sendSuccess(() -> Component.literal(
					target.getName().getString() + " has no morph form"), false);
			return Command.SINGLE_SUCCESS;
		}
		MorphService.clearActiveMorph(target, data);
		data.setMorph(null);
		persist(source, target);
		LifepathMod.LOGGER.info("admin action: {} cleared {} ({}) morph form",
				source.getTextName(), target.getName().getString(), target.getUUID());
		source.sendSuccess(() -> Component.literal("cleared "
				+ target.getName().getString() + "'s morph form"), true);
		return Command.SINGLE_SUCCESS;
	}

	/** Save + sync + catalog refresh — the SpeciesCommands.set convention. */
	private static void persist(CommandSourceStack source, ServerPlayer target) {
		CharacterManager.markDirty(target);
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		SelectionService.pushCatalog(target);
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
