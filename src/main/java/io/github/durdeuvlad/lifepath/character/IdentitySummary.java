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
		String speciesId = "", speciesName = "", speciesDesc = "", speciesIcon = "";
		SpeciesDefinition species = null;
		if (data.speciesId() != null) {
			speciesId = data.speciesId().toString();
			species = LifepathContent.species().get(data.speciesId());
			speciesName = species != null
					? species.displayName() : data.speciesId().getPath();
			if (species != null) {
				speciesDesc = species.description().orElse("");
				speciesIcon = species.icon().map(Identifier::toString).orElse("");
			}
		}

		String specId = "", specName = "", specIcon = "";
		List<IdentitySummaryPayload.Entry> focus = List.of();
		if (data.specializationId() != null) {
			specId = data.specializationId().toString();
			SpecializationDefinition spec =
					LifepathContent.specializations().get(data.specializationId());
			specName = spec != null
					? spec.displayName() : data.specializationId().getPath();
			if (spec != null) {
				specIcon = spec.icon().map(Identifier::toString).orElse("");
				focus = spec.startingSkills().keySet().stream()
						.map(IdentitySummary::entry).toList();
			}
		}

		Map<String, List<IdentitySummaryPayload.Entry>> sections = new LinkedHashMap<>();
		sections.put(SECTION_CONDITIONS, entriesOf(data.conditions()));
		sections.put(SECTION_ATTUNEMENTS, entriesOf(data.attunements()));
		sections.put(SECTION_TRAITS, entriesOf(data.traits()));

		// M6-3: display info for every ability the character owns — the HUD's
		// cooldown rows label by id; the `active` flag lets the character
		// screen offer click-to-bind only on rows the key can fire.
		Map<String, IdentitySummaryPayload.AbilityEntry> abilities =
				new LinkedHashMap<>();
		for (Identifier id : io.github.durdeuvlad.lifepath.ability.AbilityEngine
				.ownedAbilities(data)) {
			var def = LifepathContent.abilities().get(id);
			boolean active = def != null && def.trigger().kind()
					== io.github.durdeuvlad.lifepath.content.AbilityDefinition
							.Kind.ACTIVE;
			abilities.put(id.toString(), new IdentitySummaryPayload.AbilityEntry(
					id.toString(), displayName(id), iconRef(id), active));
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
					restBand, bandNames,
					rdef.icon().map(Identifier::toString).orElse("")));
		}

		return new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore(speciesId, speciesName,
						speciesDesc, speciesIcon, specId, specName, specIcon),
				focus, sections, abilities, resourceDisplays);
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
		// M9-4: unlocks[] holds species ids — show the species name, not a path.
		var species = LifepathContent.species().get(id);
		if (species != null) {
			return species.displayName();
		}
		return id.getPath();
	}

	/**
	 * Icon texture ref for any content id (M12-1): the def's {@code icon},
	 * resolved through the same domain chain as {@link #displayName} so a
	 * name and its icon can never disagree about which def they came from.
	 * Empty when the def declares none or the id resolves to nothing.
	 */
	public static String iconRef(Identifier id) {
		var ability = LifepathContent.abilities().get(id);
		if (ability != null) {
			return ability.icon().map(Identifier::toString).orElse("");
		}
		var skill = LifepathContent.skills().get(id);
		if (skill != null) {
			return skill.icon().map(Identifier::toString).orElse("");
		}
		var condition = LifepathContent.conditions().get(id);
		if (condition != null) {
			return condition.icon().map(Identifier::toString).orElse("");
		}
		var attunement = LifepathContent.attunements().get(id);
		if (attunement != null) {
			return attunement.icon().map(Identifier::toString).orElse("");
		}
		// Same terminal as displayName(): specialization/resource icons ride
		// their own payload fields (IdentityCore, ResourceDisplay), never
		// through entry() — stopping here keeps the name/icon chains equal.
		var species = LifepathContent.species().get(id);
		if (species != null) {
			return species.icon().map(Identifier::toString).orElse("");
		}
		return "";
	}

	/** One displayable reference: id + resolved name + icon ref ("" none). */
	public static IdentitySummaryPayload.Entry entry(Identifier id) {
		return new IdentitySummaryPayload.Entry(id.toString(), displayName(id),
				iconRef(id));
	}

	private static List<IdentitySummaryPayload.Entry> entriesOf(List<Identifier> ids) {
		List<IdentitySummaryPayload.Entry> entries = new ArrayList<>(ids.size());
		for (Identifier id : ids) {
			entries.add(entry(id));
		}
		return entries;
	}
}
