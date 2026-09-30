package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Data definition of a specialization — an initial focus, NOT a rigid class
 * (GAMEDESIGN §7/§19). All modifier maps are keyed by skill id and only take
 * effect through later mechanics (M2/M3); this record is pure structure.
 */
public record SpecializationDefinition(
		ResourceLocation id,
		String displayName,
		Map<ResourceLocation, Integer> startingSkills,
		Map<ResourceLocation, Aptitude> aptitudes,
		Map<ResourceLocation, Double> xpModifiers,
		Map<ResourceLocation, Double> decayModifiers,
		Map<ResourceLocation, Integer> protectedFloors,
		List<ResourceLocation> signatureRefs,
		double capacityMultiplier,
		Optional<ResourceLocation> icon,
		List<String> strengths,
		List<String> weaknesses) {

	/** Back-compatible constructor for call sites predating {@code strengths}/{@code weaknesses}. */
	public SpecializationDefinition(ResourceLocation id, String displayName,
			Map<ResourceLocation, Integer> startingSkills,
			Map<ResourceLocation, Aptitude> aptitudes,
			Map<ResourceLocation, Double> xpModifiers,
			Map<ResourceLocation, Double> decayModifiers,
			Map<ResourceLocation, Integer> protectedFloors,
			List<ResourceLocation> signatureRefs,
			double capacityMultiplier,
			Optional<ResourceLocation> icon) {
		this(id, displayName, startingSkills, aptitudes, xpModifiers,
				decayModifiers, protectedFloors, signatureRefs, capacityMultiplier,
				icon, List.of(), List.of());
	}

	/** Back-compatible constructor for call sites predating {@code icon} (M12-1). */
	public SpecializationDefinition(ResourceLocation id, String displayName,
			Map<ResourceLocation, Integer> startingSkills,
			Map<ResourceLocation, Aptitude> aptitudes,
			Map<ResourceLocation, Double> xpModifiers,
			Map<ResourceLocation, Double> decayModifiers,
			Map<ResourceLocation, Integer> protectedFloors,
			List<ResourceLocation> signatureRefs,
			double capacityMultiplier) {
		this(id, displayName, startingSkills, aptitudes, xpModifiers,
				decayModifiers, protectedFloors, signatureRefs, capacityMultiplier,
				Optional.empty(), List.of(), List.of());
	}

	/** Convenience for call sites predating {@code capacityMultiplier} (M9-3). */
	public SpecializationDefinition(ResourceLocation id, String displayName,
			Map<ResourceLocation, Integer> startingSkills,
			Map<ResourceLocation, Aptitude> aptitudes,
			Map<ResourceLocation, Double> xpModifiers,
			Map<ResourceLocation, Double> decayModifiers,
			Map<ResourceLocation, Integer> protectedFloors,
			List<ResourceLocation> signatureRefs) {
		this(id, displayName, startingSkills, aptitudes, xpModifiers,
				decayModifiers, protectedFloors, signatureRefs, 1.0);
	}

	public static SpecializationDefinition fromFile(ResourceLocation id, SpecializationDefinitionFile file) {
		return new SpecializationDefinition(id, file.displayName(), file.startingSkills(),
				file.aptitudes(), file.xpModifiers(), file.decayModifiers(), file.protectedFloors(),
				file.signatureRefs(), file.capacityMultiplier(),
				file.icon().map(raw -> IconRef.resolve("specialization", id, raw)),
				file.strengths(), file.weaknesses());
	}

	/** JSON shape of {@code data/<ns>/specialization/<name>.json} (id excluded). */
	public record SpecializationDefinitionFile(
			String displayName,
			Map<ResourceLocation, Integer> startingSkills,
			Map<ResourceLocation, Aptitude> aptitudes,
			Map<ResourceLocation, Double> xpModifiers,
			Map<ResourceLocation, Double> decayModifiers,
			Map<ResourceLocation, Integer> protectedFloors,
			List<ResourceLocation> signatureRefs,
			double capacityMultiplier,
			Optional<String> icon,
			List<String> strengths,
			List<String> weaknesses) {

		public static final Codec<SpecializationDefinitionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(SpecializationDefinitionFile::displayName),
				Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(0, 10000)).optionalFieldOf("starting_skills", Map.of()).forGetter(SpecializationDefinitionFile::startingSkills),
				Codec.unboundedMap(ResourceLocation.CODEC, Aptitude.CODEC).optionalFieldOf("aptitudes", Map.of()).forGetter(SpecializationDefinitionFile::aptitudes),
				Codec.unboundedMap(ResourceLocation.CODEC, Codec.doubleRange(0.0, 100.0)).optionalFieldOf("xp_modifiers", Map.of()).forGetter(SpecializationDefinitionFile::xpModifiers),
				Codec.unboundedMap(ResourceLocation.CODEC, Codec.doubleRange(0.0, 1.0)).optionalFieldOf("decay_modifiers", Map.of()).forGetter(SpecializationDefinitionFile::decayModifiers),
				Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(0, 10000)).optionalFieldOf("protected_floors", Map.of()).forGetter(SpecializationDefinitionFile::protectedFloors),
				ResourceLocation.CODEC.listOf().optionalFieldOf("signature", List.of()).forGetter(SpecializationDefinitionFile::signatureRefs),
			Codec.doubleRange(0.0, 100.0).optionalFieldOf("capacity_multiplier", 1.0).forGetter(SpecializationDefinitionFile::capacityMultiplier),
			Codec.STRING.optionalFieldOf("icon").forGetter(SpecializationDefinitionFile::icon),
			Codec.STRING.listOf().optionalFieldOf("strengths", List.of()).forGetter(SpecializationDefinitionFile::strengths),
			Codec.STRING.listOf().optionalFieldOf("weaknesses", List.of()).forGetter(SpecializationDefinitionFile::weaknesses)
		).apply(instance, SpecializationDefinitionFile::new));
	}
}
