package io.github.durdeuvlad.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.morph.MorphService;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lifepath species} tree (M5-1; M9-4 added the enforced player path;
 * M14 made the whole tree admin-only — players pick in the selection screen,
 * whose requests {@code SelectionService} validates against the same policy).
 *
 * <ul>
 *   <li>{@code get <player>} (admin) — show the current species and its key fields.</li>
 *   <li>{@code set <player> <id>} (admin) — set the species. Species effects are
 *       derived at eval time (ability/resource/aptitude refs), so the only
 *       cleanup a switch needs is retiring the morph pick (an active shape
 *       would lose its demorph ability); materialized resources keep
 *       ticking as owned character state. Admin override: {@code selection}
 *       rules are not enforced here.</li>
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
				// M14: admin-only surface — the player path is the GUI picker
				// (SelectionService), so the root carries the requirement.
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
											LifepathContent.species().all().keySet()
													.forEach(id -> builder.suggest(id.toString()));
											return builder.buildFuture();
										})
										.executes(ctx -> set(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "player"),
												ResourceLocationArgument.getId(ctx, "id")))))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"usage: /lifepath species get|set <player> <id>"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int get(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		ResourceLocation speciesId = data.speciesId();
		if (speciesId == null) {
			source.sendSuccess(() -> Component.literal(
					target.getName().getString() + " has no species"), false);
			return Command.SINGLE_SUCCESS;
		}
		SpeciesDefinition def = LifepathContent.species().get(speciesId);
		source.sendSuccess(() -> Component.literal(target.getName().getString()
				+ " species: " + speciesId
				+ (def == null ? " (definition missing — effects inactive)"
						: " \"" + def.displayName() + "\""
								+ " passive=" + def.passiveAbilities().size()
								+ " active=" + def.activeAbilities().size()
								+ " resources=" + def.resources().size()
								+ def.description().map(d -> " — " + d).orElse(""))), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int set(CommandSourceStack source, ServerPlayer target, ResourceLocation speciesId) {
		if (!LifepathContent.species().contains(speciesId)) {
			source.sendFailure(Component.literal("unknown species: " + speciesId));
			return 0;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		data.setSpeciesId(speciesId);
		// Species swap retires the morph pick — a stranded active shape has
		// no toggle ability left to demorph with. Same cleanup the GUI path
		// (SelectionService.selectSpecies) performs.
		MorphService.clearActiveMorph(target, data);
		data.setMorph(null);
		CharacterManager.markDirty(target);
		CharacterManager.saveCharacter(target);
		CharacterManager.syncCharacter(target);
		LifepathMod.LOGGER.info("admin action: {} set {} ({}) species to {}",
				source.getTextName(), target.getName().getString(), target.getUUID(), speciesId);
		source.sendSuccess(() -> Component.literal("set " + target.getName().getString()
				+ " species to " + speciesId), true);
		// M6-4 consequence preview: species_assigned carries only the name —
		// the recipient also gets the identity description line.
		var def = LifepathContent.species().get(speciesId);
		if (def != null && def.description().isPresent()) {
			target.displayClientMessage(Component.literal(def.description().get()), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
