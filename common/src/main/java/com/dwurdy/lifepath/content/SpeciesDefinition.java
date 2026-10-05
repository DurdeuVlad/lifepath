package com.dwurdy.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.dwurdy.lifepath.skill.Aptitude;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Data definition of a species — what a character biologically/metaphysically
 * IS (GAMEDESIGN §5/§19). Behaviorless by itself: abilities, aptitudes and
 * other refs point at content defined by their own registries (later
 * milestones). Missing refs are recorded as unresolved, never fatal.
 */
public record SpeciesDefinition(
		ResourceLocation id,
		String displayName,
		Optional<String> description,
		Visibility visibility,
		Selection selection,
		List<ResourceLocation> passiveAbilities,
		List<ResourceLocation> activeAbilities,
		Map<ResourceLocation, Aptitude> minAptitudes,
		List<ResourceLocation> resources,
		Optional<ResourceLocation> dietRules,
		Optional<ResourceLocation> mobDispositions,
		double capacityMultiplier,
		Optional<ResourceLocation> icon,
		List<String> strengths,
		List<String> weaknesses) {

	/** Back-compatible constructor for call sites predating {@code strengths}/{@code weaknesses}. */
	public SpeciesDefinition(ResourceLocation id, String displayName,
			Optional<String> description, Visibility visibility,
			Selection selection, List<ResourceLocation> passiveAbilities,
			List<ResourceLocation> activeAbilities, Map<ResourceLocation, Aptitude> minAptitudes,
			List<ResourceLocation> resources, Optional<ResourceLocation> dietRules,
			Optional<ResourceLocation> mobDispositions, double capacityMultiplier,
			Optional<ResourceLocation> icon) {
		this(id, displayName, description, visibility, selection,
				passiveAbilities, activeAbilities, minAptitudes, resources,
				dietRules, mobDispositions, capacityMultiplier, icon, List.of(), List.of());
	}

	/** Back-compatible constructor for call sites predating {@code icon} (M12-1). */
	public SpeciesDefinition(ResourceLocation id, String displayName,
			Optional<String> description, Visibility visibility,
			Selection selection, List<ResourceLocation> passiveAbilities,
			List<ResourceLocation> activeAbilities, Map<ResourceLocation, Aptitude> minAptitudes,
			List<ResourceLocation> resources, Optional<ResourceLocation> dietRules,
			Optional<ResourceLocation> mobDispositions, double capacityMultiplier) {
		this(id, displayName, description, visibility, selection,
				passiveAbilities, activeAbilities, minAptitudes, resources,
				dietRules, mobDispositions, capacityMultiplier, Optional.empty(),
				List.of(), List.of());
	}

	/** Convenience for call sites predating {@code description} (M5-1). */
	public SpeciesDefinition(ResourceLocation id, String displayName, Visibility visibility,
			Selection selection, List<ResourceLocation> passiveAbilities,
			List<ResourceLocation> activeAbilities, Map<ResourceLocation, Aptitude> minAptitudes,
			List<ResourceLocation> resources, Optional<ResourceLocation> dietRules,
			Optional<ResourceLocation> mobDispositions) {
		this(id, displayName, Optional.empty(), visibility, selection,
				passiveAbilities, activeAbilities, minAptitudes, resources,
				dietRules, mobDispositions, 1.0);
	}

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
	 * not decoded from JSON. {@code icon} is decoded leniently — a malformed
	 * value warns and drops to no-icon rather than failing the file (M12-1).
	 */
	public static SpeciesDefinition fromFile(ResourceLocation id, SpeciesDefinitionFile file) {
		return new SpeciesDefinition(id, file.displayName(), file.description(),
				file.visibility(), file.selection(),
				file.passiveAbilities(), file.activeAbilities(), file.minAptitudes(),
				file.resources(), file.dietRules(), file.mobDispositions(),
				file.capacityMultiplier(),
				file.icon().map(raw -> IconRef.resolve("species", id, raw)),
				file.strengths(), file.weaknesses());
	}

	/**
	 * JSON shape of {@code data/<ns>/species/<name>.json} (id excluded).
	 * {@code description} is the identity text the selection UX shows (M6).
	 */
	public record SpeciesDefinitionFile(
			String displayName,
			Optional<String> description,
			Visibility visibility,
			Selection selection,
			List<ResourceLocation> passiveAbilities,
			List<ResourceLocation> activeAbilities,
			Map<ResourceLocation, Aptitude> minAptitudes,
			List<ResourceLocation> resources,
			Optional<ResourceLocation> dietRules,
			Optional<ResourceLocation> mobDispositions,
			double capacityMultiplier,
			Optional<String> icon,
			List<String> strengths,
			List<String> weaknesses) {

		public static final Codec<SpeciesDefinitionFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("display_name").forGetter(SpeciesDefinitionFile::displayName),
				Codec.STRING.optionalFieldOf("description").forGetter(SpeciesDefinitionFile::description),
				Visibility.CODEC.optionalFieldOf("visibility", Visibility.NORMAL).forGetter(SpeciesDefinitionFile::visibility),
				Selection.CODEC.optionalFieldOf("selection", Selection.OPEN).forGetter(SpeciesDefinitionFile::selection),
				ResourceLocation.CODEC.listOf().optionalFieldOf("passive_abilities", List.of()).forGetter(SpeciesDefinitionFile::passiveAbilities),
				ResourceLocation.CODEC.listOf().optionalFieldOf("active_abilities", List.of()).forGetter(SpeciesDefinitionFile::activeAbilities),
				Codec.unboundedMap(ResourceLocation.CODEC, Aptitude.CODEC).optionalFieldOf("min_aptitudes", Map.of()).forGetter(SpeciesDefinitionFile::minAptitudes),
				ResourceLocation.CODEC.listOf().optionalFieldOf("resources", List.of()).forGetter(SpeciesDefinitionFile::resources),
				ResourceLocation.CODEC.optionalFieldOf("diet_rules").forGetter(SpeciesDefinitionFile::dietRules),
				ResourceLocation.CODEC.optionalFieldOf("mob_dispositions").forGetter(SpeciesDefinitionFile::mobDispositions),
			Codec.doubleRange(0.0, 100.0).optionalFieldOf("capacity_multiplier", 1.0).forGetter(SpeciesDefinitionFile::capacityMultiplier),
			Codec.STRING.optionalFieldOf("icon").forGetter(SpeciesDefinitionFile::icon),
			Codec.STRING.listOf().optionalFieldOf("strengths", List.of()).forGetter(SpeciesDefinitionFile::strengths),
			Codec.STRING.listOf().optionalFieldOf("weaknesses", List.of()).forGetter(SpeciesDefinitionFile::weaknesses)
		).apply(instance, SpeciesDefinitionFile::new));
	}
}
