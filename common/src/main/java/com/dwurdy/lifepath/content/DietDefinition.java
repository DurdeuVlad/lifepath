package com.dwurdy.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A data-defined diet rule (M5-4, GAMEDESIGN §15): which items a character may
 * draw nutrition from. Referenced by {@code species.diet_rules}; absent on a
 * species means unrestricted. Semantics are fixed and generic: items outside
 * {@code allowed} yield <b>zero nutrition</b> (no hunger/saturation) but are
 * still eaten — food side-effects unchanged. Content lives in
 * {@code data/<ns>/diet/<name>.json}.
 *
 * <p>{@code deny_message} (optional) replaces the stock actionbar line shown
 * when a food is refused — a diet can name what actually nourishes instead
 * of leaving the player guessing (M19-2).
 *
 * <p>{@code nutrition}/{@code saturation_modifier} (optional, Beta-10): when
 * &gt; 0, allowed items that aren't vanilla food become edible on
 * right-click — one item is consumed and feeds like
 * {@code FoodData.eat(nutrition, saturation_modifier)}. This is the ferrovore
 * path: ingots have no FOOD component, so vanilla never starts an eat. A
 * diet that omits them keeps the old gate-only behavior.
 */
public record DietDefinition(ResourceLocation id, List<IdTagRef> allowed,
		Optional<String> denyMessage, int nutrition,
		float saturationModifier) {

	public static DietDefinition fromFile(ResourceLocation id, DietFile file) {
		if (file.allowed().isEmpty()) {
			throw new IllegalArgumentException(
					"diet " + id + " must declare at least one allowed item/tag");
		}
		return new DietDefinition(id, file.allowed(), file.denyMessage(),
				file.nutrition(), file.saturationModifier());
	}

	/** {@code {"allowed": ["#example:foods", "minecraft:golden_apple"], "deny_message": "…"}} */
	public record DietFile(List<IdTagRef> allowed, Optional<String> denyMessage,
			int nutrition, float saturationModifier) {
		public static final Codec<DietFile> CODEC = RecordCodecBuilder.create(i -> i.group(
				IdTagRef.CODEC.listOf().fieldOf("allowed").forGetter(DietFile::allowed),
				Codec.STRING.optionalFieldOf("deny_message").forGetter(DietFile::denyMessage),
				Codec.INT.optionalFieldOf("nutrition", 0).forGetter(DietFile::nutrition),
				Codec.FLOAT.optionalFieldOf("saturation_modifier", 0.0f)
						.forGetter(DietFile::saturationModifier)
		).apply(i, DietFile::new));
	}
}
