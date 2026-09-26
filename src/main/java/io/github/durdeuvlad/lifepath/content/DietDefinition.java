package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.Identifier;

/**
 * A data-defined diet rule (M5-4, GAMEDESIGN §15): which items a character may
 * draw nutrition from. Referenced by {@code species.diet_rules}; absent on a
 * species means unrestricted. Semantics are fixed and generic: items outside
 * {@code allowed} yield <b>zero nutrition</b> (no hunger/saturation) but are
 * still eaten — food side-effects unchanged. Content lives in
 * {@code data/<ns>/diet/<name>.json}.
 */
public record DietDefinition(Identifier id, List<IdTagRef> allowed) {

	public static DietDefinition fromFile(Identifier id, DietFile file) {
		if (file.allowed().isEmpty()) {
			throw new IllegalArgumentException(
					"diet " + id + " must declare at least one allowed item/tag");
		}
		return new DietDefinition(id, file.allowed());
	}

	/** {@code {"allowed": ["#lifepath:undead_foods", "minecraft:golden_apple"]}} */
	public record DietFile(List<IdTagRef> allowed) {
		public static final Codec<DietFile> CODEC = RecordCodecBuilder.create(i -> i.group(
				IdTagRef.CODEC.listOf().fieldOf("allowed").forGetter(DietFile::allowed)
		).apply(i, DietFile::new));
	}
}
