package io.github.durdeuvlad.lifepath.ability;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.TargetContext;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * AoE target resolvers (M4-3): {@code entities_in_radius} and
 * {@code blocks_in_radius} — the multi-target complement to {@code self}.
 * Radius params clamp to {@code abilities.toml} {@code nearby_max_radius};
 * unloaded chunks are skipped rather than force-loaded.
 *
 * <p>Params: {@code radius} (required-ish, default 8 → clamped),
 * {@code entity} idOrTag filter + {@code living_only} (entity resolver),
 * {@code block} idOrTag filter (block resolver).
 */
final class BuiltinTargets {
	private BuiltinTargets() {}

	private static boolean initialized;

	static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		AbilityVocabulary.registerTarget(LifepathMod.id("entities_in_radius"), (ctx, params) ->
				io.github.durdeuvlad.lifepath.perf.PerfCounters.time(
						"scan.entities_in_radius", () -> {
			var p = ctx.self();
			String idOrTag = BuiltinConditions.str(params, "entity");
			if (p == null) {
				return List.of();
			}
			int radius = BuiltinConditions.radius(params);
			boolean livingOnly = BuiltinConditions.bool(params, "living_only", true);
			Predicate<Entity> type = entityMatcher(idOrTag);
			double r2 = radius * radius;
			List<TargetContext> out = new ArrayList<>();
			for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(radius),
					e -> !e.isSpectator()
							&& (!livingOnly || e instanceof LivingEntity)
							&& e.distanceToSqr(p) <= r2)) {
				if (type == null || type.test(e)) {
					// Player targets carry their OWN model — resource/XP
					// actions act per-target; non-players get null.
					out.add(new TargetContext(e, e.blockPosition(),
							e instanceof ServerPlayer sp
									? CharacterManager.getCharacter(sp) : null));
				}
			}
			return out;
		}));

		AbilityVocabulary.registerTarget(LifepathMod.id("blocks_in_radius"), (ctx, params) ->
				io.github.durdeuvlad.lifepath.perf.PerfCounters.time(
						"scan.blocks_in_radius", () -> {
			var p = ctx.self();
			String idOrTag = BuiltinConditions.str(params, "block");
			if (p == null || idOrTag == null) {
				return List.of(); // required filter — fail closed, don't match-all
			}
			// Tighter than nearby_max_radius: this resolver scans the whole
			// cube with no early exit, so cap hard and bound the result count.
			int radius = Math.min(BuiltinConditions.radius(params), 32);
			int limit = Math.min(Math.max(1,
					(int) BuiltinConditions.num(params, "limit", 64)), 512);
			Predicate<net.minecraft.world.level.block.state.BlockState> match = blockMatcher(idOrTag);
			if (match == null) {
				return List.of();
			}
			BlockPos center = p.blockPosition();
			List<TargetContext> out = new ArrayList<>();
			for (BlockPos pos : BlockPos.withinManhattan(center, radius, radius, radius)) {
				if (out.size() >= limit) {
					break;
				}
				if (pos.closerThan(center, radius + 0.5)
						&& p.level().hasChunkAt(pos)
						&& match.test(p.level().getBlockState(pos))) {
					// Block targets carry no character model — caster-model
					// actions (resources/XP) don't multiply per resolved block.
					out.add(new TargetContext(null, pos.immutable(), null));
				}
			}
			return out;
		}));
	}

	@org.jetbrains.annotations.Nullable
	private static Predicate<Entity> entityMatcher(@org.jetbrains.annotations.Nullable String idOrTag) {
		if (idOrTag == null) {
			return null; // absent filter matches everything
		}
		return BuiltinConditions.entityMatcher(idOrTag);
	}

	@org.jetbrains.annotations.Nullable
	private static Predicate<net.minecraft.world.level.block.state.BlockState> blockMatcher(
			@org.jetbrains.annotations.Nullable String idOrTag) {
		return BuiltinConditions.blockMatcher(idOrTag);
	}

	/** Test hook — pairs with {@link AbilityVocabulary#resetForTests}. */
	static void resetForTests() {
		initialized = false;
	}
}
