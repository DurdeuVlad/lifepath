package com.dwurdy.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Data definition of one morphable animal form — the handpicked whitelist
 * behind the Anima species' morph feature (docs/MORPH_FEATURE.md, M-1).
 * A player picks exactly one form at species-select; while morphed they
 * render as {@link #entityType}, take its hitbox, and carry the {@link #stats}
 * attribute profile (absolute base values, applied/removed by the morph
 * ability — the same convention {@code modify_attribute} actions use).
 *
 * <pre>{@code
 * {
 *   "entity_type": "minecraft:fox",
 *   "display_name": "Fox",
 *   "description": "Small, quick, and suspiciously good at stealing chickens.",
 *   "icon": "morph/fox",
 *   "stats": {
 *     "minecraft:generic.max_health": 10.0,
 *     "minecraft:generic.attack_damage": 2.0,
 *     "minecraft:generic.movement_speed": 0.3
 *   }
 * }
 * }</pre>
 *
 * <p>{@code stats} keys are vanilla attribute ids — absolute values, not
 * modifiers. Attributes absent from the map keep the player's normal values.
 * {@code generic.max_health}, when present, must be positive — proportional
 * HP carry divides by it.
 */
public record MorphFormDefinition(
		ResourceLocation id,
		ResourceLocation entityType,
		String displayName,
		Optional<String> description,
		Optional<ResourceLocation> icon,
		Map<ResourceLocation, Double> stats) {

	/**
	 * File codec: {@code id} is supplied by the loader (from the file path),
	 * not decoded from JSON. {@code icon} is decoded leniently — a malformed
	 * value warns and drops to no-icon rather than failing the file (M12-1).
	 */
	public static MorphFormDefinition fromFile(ResourceLocation id, MorphFormFile file) {
		return new MorphFormDefinition(id, file.entityType(), file.displayName(),
				file.description(),
				file.icon().map(raw -> IconRef.resolve("morph_form", id, raw)),
				Map.copyOf(file.stats()));
	}

	/** JSON shape of {@code data/<ns>/morph_form/<name>.json} (id excluded). */
	public record MorphFormFile(
			ResourceLocation entityType,
			String displayName,
			Optional<String> description,
			Optional<String> icon,
			Map<ResourceLocation, Double> stats) {

		public static final Codec<MorphFormFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("entity_type").forGetter(MorphFormFile::entityType),
				Codec.STRING.fieldOf("display_name").forGetter(MorphFormFile::displayName),
				Codec.STRING.optionalFieldOf("description").forGetter(MorphFormFile::description),
				Codec.STRING.optionalFieldOf("icon").forGetter(MorphFormFile::icon),
				Codec.unboundedMap(ResourceLocation.CODEC, Codec.doubleRange(0.0, 1.0e6))
						.optionalFieldOf("stats", Map.of()).forGetter(MorphFormFile::stats)
		).apply(instance, MorphFormFile::new));
	}
}
