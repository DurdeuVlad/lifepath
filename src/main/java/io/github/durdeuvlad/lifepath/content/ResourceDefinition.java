package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition.SpecNode;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * A data-defined player resource (M4-5, GAMEDESIGN §14/§18): a bounded meter —
 * {@code [min, max]}, a starting {@code defaultValue}, signed
 * {@code regenPerSecond} (negative = drain), and ordered {@code bands} that tie
 * meter ranges to effects. The Iceborn temperature meter is the driving case —
 * nothing here is content-specific.
 *
 * <p><b>Band semantics:</b> a band is an inclusive {@code [lo, hi]} range with
 * two effect channels:
 * <ul>
 *   <li>{@code effects[]} — sustained status effects: applied on entry and
 *       <i>refreshed every resource sweep while inside</i>, then removed when
 *       the meter leaves the band ("applies Weakness while inside" = an
 *       effects entry, not a one-shot).</li>
 *   <li>{@code actions[]} — embedded vocabulary actions fired <b>exactly once
 *       on entry</b> (a band crossing), never on exit and never per-tick.
 *       Crossing detection happens at mutation time, so relogging while inside
 *       a band does not re-fire actions (sustained effects resume on the next
 *       sweep, by design).</li>
 * </ul>
 *
 * <p>Malformed data fails the file at load: {@code min >= max}, a default
 * outside {@code [min, max]}, a non-finite regen, a reversed range, or
 * overlapping bands all reject the file rather than silently clamping.
 */
public record ResourceDefinition(Identifier id, double min, double max,
		double defaultValue, double regenPerSecond, List<Band> bands) {

	/**
	 * A sustained band status effect: {@code effect} id + {@code duration_ticks}
	 * (should exceed the resource tick interval — reapplied each sweep) +
	 * {@code amplifier}.
	 */
	public record BandEffect(Identifier effect, int durationTicks, int amplifier) {
		public static final Codec<BandEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("effect").forGetter(BandEffect::effect),
				Codec.intRange(1, 20 * 3600).optionalFieldOf("duration_ticks", 60)
						.forGetter(BandEffect::durationTicks),
				Codec.intRange(0, 255).optionalFieldOf("amplifier", 0)
						.forGetter(BandEffect::amplifier))
				.apply(i, BandEffect::new));
	}

	private static final Codec<List<Double>> RANGE = Codec.DOUBLE.listOf()
			.flatXmap(list -> list.size() == 2
					&& Double.isFinite(list.get(0)) && Double.isFinite(list.get(1))
							? DataResult.success(list)
							: DataResult.error(() -> "range must be [lo, hi] finite doubles"),
					DataResult::success);

	/** Inclusive {@code [lo, hi]} meter range + sustained effects + entry actions. */
	public record Band(double lo, double hi, List<BandEffect> effects,
			List<SpecNode> actions) {
		public static final Codec<Band> CODEC = RecordCodecBuilder.create(i -> i.group(
				RANGE.fieldOf("range").forGetter(b -> List.of(b.lo(), b.hi())),
				BandEffect.CODEC.listOf().optionalFieldOf("effects", List.of())
						.forGetter(Band::effects),
				SpecNode.CODEC.listOf().optionalFieldOf("actions", List.of())
						.forGetter(Band::actions))
				.apply(i, (range, effects, actions) -> new Band(range.get(0), range.get(1),
						effects, actions)));
	}

	/** The datapack file shape for {@code data/<ns>/resource/<name>.json}. */
	public record ResourceFile(double min, double max, double defaultValue,
			double regenPerSecond, List<Band> bands) {
		private static final Codec<Double> BOUNDED_DOUBLE =
				Codec.doubleRange(-1.0e9, 1.0e9);

		public static final Codec<ResourceFile> CODEC = RecordCodecBuilder.create(i -> i.group(
				BOUNDED_DOUBLE.fieldOf("min").forGetter(ResourceFile::min),
				BOUNDED_DOUBLE.fieldOf("max").forGetter(ResourceFile::max),
				BOUNDED_DOUBLE.optionalFieldOf("default")
						.forGetter(f -> java.util.Optional.of(f.defaultValue())),
				BOUNDED_DOUBLE.optionalFieldOf("regen_per_second", 0.0)
						.forGetter(ResourceFile::regenPerSecond),
				Band.CODEC.listOf().optionalFieldOf("bands", List.of())
						.forGetter(ResourceFile::bands))
				.apply(i, (min, max, def, regen, bands) -> new ResourceFile(min, max,
						def.orElse(min), regen, bands)));
	}

	/**
	 * Decodes + validates: {@code min < max}, default inside {@code [min,max]},
	 * band ranges ordered ({@code lo <= hi}), within {@code [min,max]}, and
	 * non-overlapping. Throws on violation — the loader ERRORs and skips.
	 */
	public static ResourceDefinition fromFile(Identifier id, ResourceFile file) {
		if (!(file.min() < file.max())) {
			throw new IllegalArgumentException("resource " + id + " requires min < max");
		}
		if (file.defaultValue() < file.min() || file.defaultValue() > file.max()) {
			throw new IllegalArgumentException("resource " + id + " default outside [min,max]");
		}
		List<Band> sorted = file.bands().stream()
				.sorted(Comparator.comparingDouble(Band::lo)).toList();
		double prevHi = Double.NEGATIVE_INFINITY;
		for (Band b : sorted) {
			if (!(b.lo() <= b.hi()) || b.lo() < file.min() || b.hi() > file.max()) {
				throw new IllegalArgumentException("resource " + id
						+ " band [" + b.lo() + "," + b.hi() + "] must satisfy min<=lo<=hi<=max");
			}
			if (b.lo() <= prevHi) {
				throw new IllegalArgumentException("resource " + id + " has overlapping bands");
			}
			prevHi = b.hi();
		}
		return new ResourceDefinition(id, file.min(), file.max(), file.defaultValue(),
				file.regenPerSecond(), sorted);
	}

	/** Index of the band containing {@code value}, or -1 when in no band. */
	public static int bandOf(@Nullable ResourceDefinition def, double value) {
		if (def == null) {
			return -1;
		}
		for (int i = 0; i < def.bands().size(); i++) {
			Band b = def.bands().get(i);
			if (value >= b.lo() && value <= b.hi()) {
				return i;
			}
		}
		return -1;
	}
}
