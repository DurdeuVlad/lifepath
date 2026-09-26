package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.Aptitude;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;

/**
 * Data definition of a specialization — an initial focus, NOT a rigid class
 * (GAMEDESIGN §7/§19). All modifier maps are keyed by skill id and only take
 * effect through later mechanics (M2/M3); this record is pure structure.
 */
public record SpecializationDefinition(
		Identifier id,
		String displayName,
		Map<Identifier, Integer> startingSkills,
		Map<Identifier, Aptitude> aptitudes,
		Map<Identifier, Double> xpModifiers,
		Map<Identifier, Double> decayModifiers,
		Map<Identifier, Integer> protectedFloors,
		List<Identifier> signatureRefs) {

	public static SpecializationDefinition fromFile(Identifier id, SpecializationDefinitionFile file) {
		return new SpecializationDefinition(id, file.displayName(), file.startingSkills(),
				file.aptitudes(), file.xpModifiers(), file.decayModifiers(), file.protectedFloors(),
				file.signatureRefs());
	}

	/** JSON shape of {@code data/<ns>/specialization/<name>.json} (id excluded). */
	public record SpecializationDefinitionFile(
			String displayName,
			Map<Identifier, Integer> startingSkills,
			Map<Identifier, Aptitude> aptitudes,
			Map<Identifier, Double> xpModifiers,
			Map<Identifier, Double> decayModifiers,
			Map<Identifier, Integer> protectedFloors,
			List<Identifier> signatureRefs) {

		public static final Codec<SpecializationDefinitionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(SpecializationDefinitionFile::displayName),
				Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("starting_skills", Map.of()).forGetter(SpecializationDefinitionFile::startingSkills),
				Codec.unboundedMap(Identifier.CODEC, Aptitude.CODEC).optionalFieldOf("aptitudes", Map.of()).forGetter(SpecializationDefinitionFile::aptitudes),
				Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("xp_modifiers", Map.of()).forGetter(SpecializationDefinitionFile::xpModifiers),
				Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("decay_modifiers", Map.of()).forGetter(SpecializationDefinitionFile::decayModifiers),
				Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("protected_floors", Map.of()).forGetter(SpecializationDefinitionFile::protectedFloors),
				Identifier.CODEC.listOf().optionalFieldOf("signature", List.of()).forGetter(SpecializationDefinitionFile::signatureRefs)
		).apply(instance, SpecializationDefinitionFile::new));
	}
}
