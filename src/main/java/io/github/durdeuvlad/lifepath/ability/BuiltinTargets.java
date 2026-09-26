package io.github.durdeuvlad.lifepath.ability;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.TargetContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

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

		AbilityVocabulary.registerTarget(LifepathMod.id("entities_in_radius"), (ctx, params) -> {
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
			for (Entity e : p.getWorld().getOtherEntities(p, p.getBoundingBox().expand(radius),
					e -> !e.isSpectator()
							&& (!livingOnly || e instanceof LivingEntity)
							&& e.squaredDistanceTo(p) <= r2)) {
				if (type == null || type.test(e)) {
					out.add(new TargetContext(e, e.getBlockPos(), ctx.data()));
				}
			}
			return out;
		});

		AbilityVocabulary.registerTarget(LifepathMod.id("blocks_in_radius"), (ctx, params) -> {
			var p = ctx.self();
			String idOrTag = BuiltinConditions.str(params, "block");
			if (p == null) {
				return List.of();
			}
			int radius = BuiltinConditions.radius(params);
			Predicate<net.minecraft.block.BlockState> match = blockMatcher(idOrTag);
			if (match == null) {
				return List.of();
			}
			BlockPos center = p.getBlockPos();
			List<TargetContext> out = new ArrayList<>();
			for (BlockPos pos : BlockPos.iterateOutwards(center, radius, radius, radius)) {
				if (pos.isWithinDistance(center, radius + 0.5)
						&& p.getWorld().isChunkLoaded(pos)
						&& match.test(p.getWorld().getBlockState(pos))) {
					out.add(new TargetContext(null, pos.toImmutable(), ctx.data()));
				}
			}
			return out;
		});
	}

	@org.jetbrains.annotations.Nullable
	private static Predicate<Entity> entityMatcher(@org.jetbrains.annotations.Nullable String idOrTag) {
		if (idOrTag == null) {
			return null; // absent filter matches everything
		}
		return BuiltinConditions.entityMatcher(idOrTag);
	}

	@org.jetbrains.annotations.Nullable
	private static Predicate<net.minecraft.block.BlockState> blockMatcher(
			@org.jetbrains.annotations.Nullable String idOrTag) {
		return BuiltinConditions.blockMatcher(idOrTag);
	}

	/** Test hook — pairs with {@link AbilityVocabulary#resetForTests}. */
	static void resetForTests() {
		initialized = false;
	}
}
