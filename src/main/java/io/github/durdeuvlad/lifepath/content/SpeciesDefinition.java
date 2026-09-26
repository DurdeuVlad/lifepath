package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.Aptitude;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Identifier;

/**
 * Data definition of a species — what a character biologically/metaphysically
 * IS (GAMEDESIGN §5/§19). Behaviorless by itself: abilities, aptitudes and
 * other refs point at content defined by their own registries (later
 * milestones). Missing refs are recorded as unresolved, never fatal.
 */
public record SpeciesDefinition(
		Identifier id,
		String displayName,
		Visibility visibility,
		Selection selection,
		List<Identifier> passiveAbilities,
		List<Identifier> activeAbilities,
		Map<Identifier, Aptitude> minAptitudes,
		List<Identifier> resources,
		Optional<Identifier> dietRules,
		Optional<Identifier> mobDispositions) {

	/** How the species appears in selection UI. */
	public enum Visibility {
		NORMAL, HIDDEN;

		public static final Codec<Visibility> CODEC = Codec.STRING.xmap(
				name -> Visibility.valueOf(name.toUpperCase(Locale.ROOT)),
				visibility -> visibility.name().toLowerCase(Locale.ROOT));
	}

	/** Who may pick the species at character creation. */
	public enum Selection {
		OPEN, ADMIN_ONLY, UNLOCKED;

		public static final Codec<Selection> CODEC = Codec.STRING.xmap(
				name -> Selection.valueOf(name.toUpperCase(Locale.ROOT)),
				selection -> selection.name().toLowerCase(Locale.ROOT));
	}

	/**
	 * File codec: {@code id} is supplied by the loader (from the file path),
	 * not decoded from JSON.
	 */
	public static SpeciesDefinition fromFile(Identifier id, SpeciesDefinitionFile file) {
		return new SpeciesDefinition(id, file.displayName(), file.visibility(), file.selection(),
				file.passiveAbilities(), file.activeAbilities(), file.minAptitudes(), file.resources(),
				file.dietRules(), file.mobDispositions());
	}

	/** JSON shape of {@code data/<ns>/species/<name>.json} (id excluded). */
	public record SpeciesDefinitionFile(
			String displayName,
			Visibility visibility,
			Selection selection,
			List<Identifier> passiveAbilities,
			List<Identifier> activeAbilities,
			Map<Identifier, Aptitude> minAptitudes,
			List<Identifier> resources,
			Optional<Identifier> dietRules,
			Optional<Identifier> mobDispositions) {

		public static final Codec<SpeciesDefinitionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(SpeciesDefinitionFile::displayName),
				Visibility.CODEC.optionalFieldOf("visibility", Visibility.NORMAL).forGetter(SpeciesDefinitionFile::visibility),
				Selection.CODEC.optionalFieldOf("selection", Selection.OPEN).forGetter(SpeciesDefinitionFile::selection),
				Identifier.CODEC.listOf().optionalFieldOf("passive_abilities", List.of()).forGetter(SpeciesDefinitionFile::passiveAbilities),
				Identifier.CODEC.listOf().optionalFieldOf("active_abilities", List.of()).forGetter(SpeciesDefinitionFile::activeAbilities),
				Codec.unboundedMap(Identifier.CODEC, Aptitude.CODEC).optionalFieldOf("min_aptitudes", Map.of()).forGetter(SpeciesDefinitionFile::minAptitudes),
				Identifier.CODEC.listOf().optionalFieldOf("resources", List.of()).forGetter(SpeciesDefinitionFile::resources),
				Identifier.CODEC.optionalFieldOf("diet_rules").forGetter(SpeciesDefinitionFile::dietRules),
				Identifier.CODEC.optionalFieldOf("mob_dispositions").forGetter(SpeciesDefinitionFile::mobDispositions)
		).apply(instance, SpeciesDefinitionFile::new));
	}
}
