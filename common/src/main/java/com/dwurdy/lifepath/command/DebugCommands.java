package com.dwurdy.lifepath.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityEngine;
import com.dwurdy.lifepath.ability.AbilityVocabulary;
import com.dwurdy.lifepath.ability.CooldownService;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.skill.DiminishingReturns;
import com.dwurdy.lifepath.skill.SkillDecayService;
import com.dwurdy.lifepath.skill.SkillProgress;
import com.dwurdy.lifepath.skill.SkillService;
import com.dwurdy.lifepath.skill.SkillXpService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * M7-4 operator debug tooling. {@code /lifepath debug …} reports EFFECTIVE
 * live state (post-modifier, post-decay-projection) — the whole point is to
 * answer "why is this player seeing X" without a debugger. Everything is
 * permission-2 gated and console-safe (entity-selector args, never
 * {@code getPlayer()}).
 *
 * <ul>
 *   <li>{@code debug character <player>} — full dump + modifiers in effect.
 *   <li>{@code debug ability <player> <ability>} — ownership, per-condition
 *       live result, cooldown state.
 *   <li>{@code debug skill <player> <skill>} — level/xp/floor/aptitude,
 *       last-use, decay projection, diminishing-returns multiplier.
 *   <li>{@code cooldown clear <player> [ability]} — clears one or all
 *       cooldowns; the admin action is logged.
 * </ul>
 */
public final class DebugCommands {
	private static boolean initialized;

	private DebugCommands() {
	}

	/** Contributes the {@code debug} + {@code cooldown} subcommand trees. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		var debug = literal("debug")
				.requires(src -> src.hasPermission(LifepathCommands.ADMIN_PERMISSION))
				.executes(ctx -> usage(ctx.getSource()));
		debug.then(literal("character")
				.then(argument("player", EntityArgument.player())
						.executes(ctx -> character(ctx.getSource(),
								EntityArgument.getPlayer(ctx, "player")))));
		debug.then(literal("ability")
				.then(argument("player", EntityArgument.player())
						.then(argument("ability", ResourceLocationArgument.id())
								.executes(ctx -> ability(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player"),
										ResourceLocationArgument.getId(ctx, "ability"))))));
		debug.then(literal("skill")
				.then(argument("player", EntityArgument.player())
						.then(argument("skill", ResourceLocationArgument.id())
								.executes(ctx -> skill(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player"),
										ResourceLocationArgument.getId(ctx, "skill")))
								.then(literal("set")
										.then(argument("level",
												com.mojang.brigadier.arguments.IntegerArgumentType
														.integer(0))
												.executes(ctx -> skillSet(ctx.getSource(),
														EntityArgument.getPlayer(ctx, "player"),
														ResourceLocationArgument.getId(ctx, "skill"),
														com.mojang.brigadier.arguments.IntegerArgumentType
																.getInteger(ctx, "level"))))))));
		debug.then(literal("open")
				.then(argument("player", EntityArgument.player())
						.then(argument("pos", BlockPosArgument.blockPos())
								.executes(ctx -> openContainer(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player"),
										BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));
		debug.then(literal("forge")
				.then(argument("pos", BlockPosArgument.blockPos())
						.executes(ctx -> forgeNow(ctx.getSource(),
								BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
		LifepathCommands.register(debug);
		LifepathCommands.register(literal("cooldown")
				.requires(src -> src.hasPermission(LifepathCommands.ADMIN_PERMISSION))
				.then(literal("clear")
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> clearAll(ctx.getSource(),
										EntityArgument.getPlayer(ctx, "player")))
								.then(argument("ability", ResourceLocationArgument.id())
										.executes(ctx -> clearOne(ctx.getSource(),
												EntityArgument.getPlayer(ctx, "player"),
												ResourceLocationArgument.getId(ctx, "ability")))))));
	}

	private static int usage(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal(
				"usage: /lifepath debug character|ability|skill <player> [id]"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int character(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		long now = System.currentTimeMillis();
		// Charge lazy decay first — the dump shows post-decay truth.
		if (SkillDecayService.applyLazyAll(data, now) > 0) {
			CharacterManager.markDirty(target);
			CharacterManager.syncCharacter(target);
		}
		source.sendSuccess(() -> Component.literal("Lifepath debug character: "
				+ target.getName().getString() + " (" + target.getUUID() + ")"), false);
		for (Component line : CharacterCommands.describe(data, now)) {
			source.sendSuccess(() -> line, false);
		}
		// Modifiers in effect: the XP pipeline ids (registration order = eval order).
		source.sendSuccess(() -> Component.literal("  xp modifiers: "
				+ com.dwurdy.lifepath.skill.SkillXpService.modifierIds()), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int ability(CommandSourceStack source, ServerPlayer target,
			ResourceLocation abilityId) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		long now = System.currentTimeMillis();
		source.sendSuccess(() -> Component.literal("Lifepath debug ability " + abilityId
				+ " for " + target.getName().getString()), false);
		AbilityDefinition def = LifepathContent.abilities().get(abilityId);
		boolean owned = AbilityEngine.ownedAbilities(data).contains(abilityId);
		source.sendSuccess(() -> Component.literal("  owned=" + owned
				+ "  defined=" + (def != null)
				+ (def == null ? "" : "  enabled=" + def.enabled()
						+ "  trigger=" + def.trigger().kind())), false);
		if (def == null) {
			return Command.SINGLE_SUCCESS;
		}
		// Per-condition live results — the operator's "why won't it fire".
		var ctx = new AbilityVocabulary.EvalContext(target, data, now, abilityId);
		for (AbilityDefinition.SpecNode cond : def.conditions().all()) {
			String result = evalCondition(cond, ctx);
			source.sendSuccess(() -> Component.literal(
					"  all: " + cond.type() + " -> " + result), false);
		}
		for (AbilityDefinition.SpecNode cond : def.conditions().any()) {
			String result = evalCondition(cond, ctx);
			source.sendSuccess(() -> Component.literal(
					"  any: " + cond.type() + " -> " + result), false);
		}
		long cd = CooldownService.remainingMillis(data, abilityId, now);
		source.sendSuccess(() -> Component.literal("  cooldown: "
				+ (cd > 0 ? cd + "ms remaining" : "ready")), false);
		return Command.SINGLE_SUCCESS;
	}

	/** Evaluates one condition with fail-closed isolation — mirrors the engine's semantics. */
	private static String evalCondition(AbilityDefinition.SpecNode cond,
			AbilityVocabulary.EvalContext ctx) {
		var eval = AbilityVocabulary.condition(cond.type());
		if (eval == null) {
			return "unknown-type";
		}
		try {
			return Boolean.toString(eval.test(ctx, cond.raw()));
		} catch (Exception e) {
			return "threw:" + e.getClass().getSimpleName();
		}
	}

	/** {@code debug skill <player> <skill> set <level>} — admin override for
	 * testing/band verification; goes through the normal level-set path so
	 * sync, milestones, and clamping all behave as if it were earned. */
	private static int skillSet(CommandSourceStack source, ServerPlayer target,
			ResourceLocation skillId, int level) {
		var result = SkillXpService.setLevel(target, skillId, level,
				com.dwurdy.lifepath.event.ActivityEvent.of(
						LifepathMod.id("admin_set"), skillId));
		source.sendSuccess(() -> Component.literal("set " + skillId + " for "
				+ target.getName().getString() + ": " + result.oldLevel()
				+ " -> " + result.newLevel()
				+ (result.applied() ? "" : " (no change)")), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int skill(CommandSourceStack source, ServerPlayer target,
			ResourceLocation skillId) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		long now = System.currentTimeMillis();
		source.sendSuccess(() -> Component.literal("Lifepath debug skill " + skillId
				+ " for " + target.getName().getString()), false);
		SkillProgress p = data.skill(skillId);
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		if (p == null) {
			source.sendSuccess(() -> Component.literal("  <no progress recorded>"), false);
		} else {
			SkillProgress pp = p;
			source.sendSuccess(() -> Component.literal("  level=" + pp.level()
					+ "  xp=" + pp.xp()
					+ "  highest=" + pp.highestLevel()
					+ "  floor=" + pp.protectedFloor()
					+ "  aptitude=" + pp.aptitude()
					+ "  effective_aptitude="
					+ SkillService.effectiveAptitude(data.speciesId(), skillId, pp)), false);
			source.sendSuccess(() -> Component.literal("  last_use="
					+ (pp.lastMeaningfulUse() <= 0 ? "<never>"
							: java.time.Instant.ofEpochMilli(pp.lastMeaningfulUse()).toString())
					+ "  checkpoint=" + (pp.lastDecayCheckpoint() <= 0 ? "<never>"
							: java.time.Instant.ofEpochMilli(pp.lastDecayCheckpoint()).toString())),
					false);
			if (def != null && SkillDecayService.enabled()) {
				double projected = SkillDecayService
						.decayedFractionalLevel(pp, def, data, now);
				source.sendSuccess(() -> Component.literal("  decay projection: frac="
						+ String.format("%.2f", projected)
						+ " (floor " + pp.protectedFloor() + ")"), false);
			} else {
				source.sendSuccess(() -> Component.literal("  decay projection: <disabled or undefined>"),
						false);
			}
		}
		// Diminishing returns: count in-window hits for signatures naming this skill.
		long cutoff = now - DiminishingReturns.windowMs();
		List<String> hits = new ArrayList<>();
		data.actionSignatures().forEach((sig, times) -> {
			if (sig.contains(skillId.toString())) {
				long active = times.stream().filter(t -> t > cutoff).count();
				if (active > 0) {
					hits.add(sig + " count=" + active
							+ " mult=" + DiminishingReturns.multiplierFor((int) active));
				}
			}
		});
		if (hits.isEmpty()) {
			source.sendSuccess(() -> Component.literal("  diminishing returns: <no in-window hits>"),
					false);
		} else {
			for (String hit : hits) {
				source.sendSuccess(() -> Component.literal("  diminishing: " + hit), false);
			}
		}
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * {@code debug open <player> <pos>} — opens the block's menu for the
	 * player without needing an in-world right-click (headless/RDP UI
	 * automation can't inject captured-cursor clicks). Any MenuProvider
	 * block entity works: brewing stands, furnaces, anvils.
	 */
	private static int openContainer(CommandSourceStack source, ServerPlayer target,
			net.minecraft.core.BlockPos pos) {
		// Block entity menus first (brewing stand, anvil); state-backed menus
		// second (drafting tables and other BE-less providers).
		var be = target.level().getBlockEntity(pos);
		net.minecraft.world.MenuProvider provider =
				be instanceof net.minecraft.world.MenuProvider p ? p
						: target.level().getBlockState(pos)
								.getMenuProvider(target.level(), pos);
		if (provider == null) {
			source.sendFailure(Component.literal(
					"no menu provider at " + pos.toShortString()
							+ (be == null ? " (no block entity)" : "")));
			return 0;
		}
		// NeoForge's modded menus need the block pos in extraData — vanilla
		// openMenu leaves it null and the client NPEs ("Network Protocol
		// Error"). The (provider, pos) overload only exists on NeoForge's
		// patched Player, so resolve it reflectively and fall back on Fabric.
		java.util.OptionalInt opened;
		try {
			var m = target.getClass().getMethod("openMenu",
					net.minecraft.world.MenuProvider.class,
					net.minecraft.core.BlockPos.class);
			opened = (java.util.OptionalInt) m.invoke(target, provider, pos);
		} catch (NoSuchMethodException e) {
			opened = target.openMenu(provider);
		} catch (ReflectiveOperationException e) {
			source.sendFailure(Component.literal("open failed: " + e.getCause()));
			return 0;
		}
		java.util.OptionalInt result = opened;
		source.sendSuccess(() -> Component.literal("opened "
				+ provider.getDisplayName().getString() + " for "
				+ target.getName().getString() + " (containerId="
				+ (result.isPresent() ? result.getAsInt() : "rejected") + ")"), false);
		return result.isPresent() ? Command.SINGLE_SUCCESS : 0;
	}

	/**
	 * {@code debug forge <pos>} — invokes the block entity's {@code craftItem()}
	 * reflectively (Overgeared smithing anvil or any class exposing it). Runs
	 * through the real mixin path, so material-gate denial, XP award, and
	 * outcome scaling all behave exactly as a finished hammer-hit sequence.
	 */
	private static int forgeNow(CommandSourceStack source,
			net.minecraft.core.BlockPos pos) {
		var be = source.getLevel().getBlockEntity(pos);
		if (be == null) {
			source.sendFailure(Component.literal("no block entity at " + pos.toShortString()));
			return 0;
		}
		Class<?> cls = be.getClass();
		while (cls != null) {
			try {
				var m = cls.getDeclaredMethod("craftItem");
				m.setAccessible(true);
				m.invoke(be);
				source.sendSuccess(() -> Component.literal(
						"craftItem invoked on " + be.getClass().getSimpleName()
								+ " at " + pos.toShortString()), false);
				// Diagnostics: report whether a forging recipe still matches
				// and what the output slot holds — distinguishes "gate denied"
				// from "recipe didn't match" without guessing.
				Class<?> rc = be.getClass();
				while (rc != null) {
					try {
						var gm = rc.getDeclaredMethod("getCurrentRecipe");
						gm.setAccessible(true);
						Object r = gm.invoke(be);
						source.sendSuccess(() -> Component.literal(
								"  currentRecipe=" + r), false);
						break;
					} catch (NoSuchMethodException e) {
						rc = rc.getSuperclass();
					} catch (ReflectiveOperationException e) {
						source.sendSuccess(() -> Component.literal(
								"  currentRecipe threw: " + e.getCause()), false);
						break;
					}
				}
				if (be instanceof net.minecraft.world.Container c) {
					source.sendSuccess(() -> Component.literal(
							"  output=" + c.getItem(10)), false);
				}
				return Command.SINGLE_SUCCESS;
			} catch (NoSuchMethodException e) {
				cls = cls.getSuperclass();
			} catch (ReflectiveOperationException e) {
				source.sendFailure(Component.literal("craftItem threw: " + e.getCause()));
				return 0;
			}
		}
		source.sendFailure(Component.literal(
				be.getClass().getSimpleName() + " has no craftItem()"));
		return 0;
	}

	private static int clearAll(CommandSourceStack source, ServerPlayer target) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		int n = CooldownService.clearAll(data);
		CharacterManager.markDirty(target);
		CharacterManager.syncCharacter(target);
		LifepathMod.LOGGER.info("admin action: {} cleared all cooldowns for {} ({})",
				source.getTextName(), target.getName().getString(), target.getUUID());
		source.sendSuccess(() -> Component.literal(
				"cleared " + n + " cooldowns for " + target.getName().getString()), true);
		return Command.SINGLE_SUCCESS;
	}

	private static int clearOne(CommandSourceStack source, ServerPlayer target,
			ResourceLocation abilityId) {
		PlayerCharacterData data = CharacterManager.getCharacter(target);
		boolean cleared = CooldownService.clear(data, abilityId);
		if (cleared) {
			CharacterManager.markDirty(target);
			CharacterManager.syncCharacter(target);
		}
		LifepathMod.LOGGER.info("admin action: {} cleared cooldown {} for {} ({}) [{}]",
				source.getTextName(), abilityId, target.getName().getString(),
				target.getUUID(), cleared ? "cleared" : "absent");
		source.sendSuccess(() -> Component.literal(cleared
				? "cleared cooldown " + abilityId + " for " + target.getName().getString()
				: "no cooldown on " + abilityId + " for " + target.getName().getString()), true);
		return cleared ? Command.SINGLE_SUCCESS : 0;
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
