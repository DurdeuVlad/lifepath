package io.github.durdeuvlad.lifepath.character;

import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;

/**
 * Resolves a player's identity state into display strings for
 * {@link IdentitySummaryPayload} (M6-1). All lookups are server-side registry
 * reads — missing or deleted definitions degrade to the raw id path instead
 * of failing. The client never sees content it doesn't own: the summary is
 * built per-player and sent only to that player.
 */
public final class IdentitySummary {
	private IdentitySummary() {
	}

	/** Well-known section keys for {@link IdentitySummaryPayload#sections()}. */
	public static final String SECTION_CONDITIONS = "conditions";
	public static final String SECTION_ATTUNEMENTS = "attunements";
	public static final String SECTION_TRAITS = "traits";

	/** Builds the display summary for {@code data}; never null. */
	public static IdentitySummaryPayload build(PlayerCharacterData data) {
		String speciesId = "", speciesName = "", speciesDesc = "";
		SpeciesDefinition species = null;
		if (data.speciesId() != null) {
			speciesId = data.speciesId().toString();
			species = LifepathContent.species().get(data.speciesId());
			speciesName = species != null
					? species.displayName() : data.speciesId().getPath();
			if (species != null) {
				speciesDesc = species.description().orElse("");
			}
		}

		String specId = "", specName = "";
		List<String> focus = List.of();
		if (data.specializationId() != null) {
			specId = data.specializationId().toString();
			SpecializationDefinition spec =
					LifepathContent.specializations().get(data.specializationId());
			specName = spec != null
					? spec.displayName() : data.specializationId().getPath();
			if (spec != null) {
				focus = spec.startingSkills().keySet().stream()
						.map(IdentitySummary::displayName).toList();
			}
		}

		Map<String, List<String>> sections = new LinkedHashMap<>();
		sections.put(SECTION_CONDITIONS, namesOf(data.conditions()));
		sections.put(SECTION_ATTUNEMENTS, namesOf(data.attunements()));
		sections.put(SECTION_TRAITS, namesOf(data.traits()));

		// M6-3: display names for every ability the character owns — the HUD's
		// cooldown rows label by id; plus static resource display info.
		Map<String, String> abilityNames = new LinkedHashMap<>();
		for (Identifier id : io.github.durdeuvlad.lifepath.ability.AbilityEngine
				.ownedAbilities(data)) {
			abilityNames.put(id.toString(), displayName(id));
		}
		List<IdentitySummaryPayload.ResourceDisplay> resourceDisplays =
				new ArrayList<>();
		java.util.Set<Identifier> resourceIds = new java.util.LinkedHashSet<>(
				data.resources().keySet());
		if (species != null) {
			resourceIds.addAll(species.resources());
		}
		for (Identifier rid : resourceIds) {
			var rdef = LifepathContent.resources().get(rid);
			if (rdef == null) {
				continue;
			}
			List<String> bandNames = rdef.bands().stream()
					.map(io.github.durdeuvlad.lifepath.content.ResourceDefinition
							.Band::name).toList();
			int restBand = -1;
			for (int i = 0; i < rdef.bands().size(); i++) {
				var b = rdef.bands().get(i);
				if (rdef.defaultValue() >= b.lo() && rdef.defaultValue() <= b.hi()) {
					restBand = i;
					break;
				}
			}
			resourceDisplays.add(new IdentitySummaryPayload.ResourceDisplay(
					rid.toString(), rdef.displayName(), rdef.defaultValue(),
					restBand, bandNames));
		}

		return new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore(speciesId, speciesName,
						speciesDesc, specId, specName),
				focus, sections, abilityNames, resourceDisplays);
	}

	/**
	 * Display name for any content id: ability → {@code displayName}, skill →
	 * {@code displayName}, else the id path. Never throws on unknown content.
	 */
	public static String displayName(Identifier id) {
		var ability = LifepathContent.abilities().get(id);
		if (ability != null) {
			return ability.displayName();
		}
		var skill = LifepathContent.skills().get(id);
		if (skill != null) {
			return skill.displayName();
		}
		var condition = LifepathContent.conditions().get(id);
		if (condition != null) {
			return condition.displayName();
		}
		var attunement = LifepathContent.attunements().get(id);
		if (attunement != null) {
			return attunement.displayName();
		}
		return id.getPath();
	}

	private static List<String> namesOf(List<Identifier> ids) {
		List<String> names = new ArrayList<>(ids.size());
		for (Identifier id : ids) {
			names.add(displayName(id));
		}
		return names;
	}
}
