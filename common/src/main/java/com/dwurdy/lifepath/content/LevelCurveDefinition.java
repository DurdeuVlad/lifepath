package com.dwurdy.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Data-defined XP→level curve (TIMELINE §5, M2-2). A curve is a TABLE, not a
 * formula: {@code thresholds[L]} is the cumulative total XP required to reach
 * level {@code L}. Formulas belong in whatever tool authors use to generate the
 * table — never in engine code.
 *
 * <p>File shape ({@code data/<ns>/skill/curve/<name>.json}):
 * <pre>{@code
 * { "thresholds": [0, 40, 95, 165, ...] }
 * }</pre>
 *
 * <p>Constraints: non-empty, {@code thresholds[0] == 0}, strictly increasing,
 * all finite. The highest reachable level is {@code thresholds.size() - 1};
 * a skill's own {@code max_level} caps it further. XP above the last threshold
 * stays at the last level.
 */
public record LevelCurveDefinition(ResourceLocation id, List<Double> thresholds) {

	public static LevelCurveDefinition fromFile(ResourceLocation id, LevelCurveFile file) {
		validate(id, file.thresholds());
		return new LevelCurveDefinition(id, List.copyOf(file.thresholds()));
	}

	private static void validate(ResourceLocation id, List<Double> t) {
		if (t.size() < 2 || t.get(0) != 0.0) {
			throw new IllegalArgumentException(id
					+ ": thresholds must have at least 2 entries with thresholds[0]==0"
					+ " (a single-entry table would pin the skill at level 0 forever)");
		}
		for (int i = 0; i < t.size(); i++) {
			double v = t.get(i);
			if (!Double.isFinite(v) || v < 0) {
				throw new IllegalArgumentException(id + ": threshold " + i + " must be a finite non-negative number");
			}
			if (i > 0 && v <= t.get(i - 1)) {
				throw new IllegalArgumentException(id + ": thresholds must be strictly increasing (index " + i + ")");
			}
		}
	}

	/** The largest level reachable on this curve. */
	public int maxAttainableLevel() {
		return thresholds.size() - 1;
	}

	/** Total XP required to reach {@code level} (clamped to the table's range). */
	public double xpForLevel(int level) {
		int idx = Math.max(0, Math.min(level, thresholds.size() - 1));
		return thresholds.get(idx);
	}

	/** Level for a total XP amount: the highest {@code L} with {@code xp >= thresholds[L]}. */
	public int levelFor(double xp) {
		if (Double.isNaN(xp) || xp < 0) {
			return 0;
		}
		if (xp == Double.POSITIVE_INFINITY) {
			return thresholds.size() - 1; // saturated: as maxed as this curve gets
		}
		int level = 0;
		for (int i = 1; i < thresholds.size(); i++) {
			if (xp >= thresholds.get(i)) {
				level = i;
			} else {
				break;
			}
		}
		return level;
	}

	public record LevelCurveFile(List<Double> thresholds) {
		public static final Codec<LevelCurveFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.DOUBLE.listOf().fieldOf("thresholds").forGetter(LevelCurveFile::thresholds)
		).apply(instance, LevelCurveFile::new));
	}
}
