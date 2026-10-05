package com.dwurdy.lifepath.character;

import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import com.dwurdy.lifepath.network.s2c.IdentitySummaryPayload;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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

	/**
	 * Display text as translatable-with-fallback: bundled content carries
	 * lang keys ({@code <namespace>.<domain>.<path>.<suffix>}) so every
	 * client renders its own locale; the datapack-authored literal rides
	 * as the fallback, so custom content still works without a
	 * resourcepack. This is the same convention Origins-style mods use —
	 * key indirection — except the fallback keeps unknown content legible
	 * instead of showing a raw key.
	 */
	public static Component keyedText(ResourceLocation id, String domain,
			String suffix, String fallback) {
		return Component.translatableWithFallback(
				id.getNamespace() + "." + domain + "." + id.getPath() + "." + suffix,
				fallback);
	}

	/** Localizable display name for any content id — the {@code .name}
	 *  suffix under the id's registry domain; raw literal when the id is
	 *  unknown to every registry. */
	public static Component displayNameComponent(ResourceLocation id) {
		String domain = domainOf(id);
		return domain == null
				? Component.literal(displayName(id))
				: keyedText(id, domain, "name", displayName(id));
	}

	private static @Nullable String domainOf(ResourceLocation id) {
		if (LifepathContent.abilities().get(id) != null) {
			return "ability";
		}
		if (LifepathContent.skills().get(id) != null) {
			return "skill";
		}
		if (LifepathContent.conditions().get(id) != null) {
			return "condition";
		}
		if (LifepathContent.attunements().get(id) != null) {
			return "attunement";
		}
		if (LifepathContent.species().get(id) != null) {
			return "species";
		}
		if (LifepathContent.specializations().get(id) != null) {
			return "specialization";
		}
		if (LifepathContent.resources().get(id) != null) {
			return "resource";
		}
		if (LifepathContent.morphForms().get(id) != null) {
			return "morph_form";
		}
		return null;
	}

	/** Builds the display summary for {@code data}; never null. */
	public static IdentitySummaryPayload build(PlayerCharacterData data) {
		String speciesId = "", speciesIcon = "";
		Component speciesName = Component.empty(), speciesDesc = Component.empty();
		SpeciesDefinition species = null;
		if (data.speciesId() != null) {
			speciesId = data.speciesId().toString();
			species = LifepathContent.species().get(data.speciesId());
			speciesName = keyedText(data.speciesId(), "species", "name",
					species != null ? species.displayName() : data.speciesId().getPath());
			if (species != null) {
				speciesDesc = keyedText(data.speciesId(), "species", "description",
						species.description().orElse(""));
				speciesIcon = species.icon().map(ResourceLocation::toString).orElse("");
			}
		}

		String specId = "", specIcon = "";
		Component specName = Component.empty();
		List<IdentitySummaryPayload.Entry> focus = List.of();
		if (data.specializationId() != null) {
			specId = data.specializationId().toString();
			SpecializationDefinition spec =
					LifepathContent.specializations().get(data.specializationId());
			specName = keyedText(data.specializationId(), "specialization", "name",
					spec != null ? spec.displayName() : data.specializationId().getPath());
			if (spec != null) {
				specIcon = spec.icon().map(ResourceLocation::toString).orElse("");
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
		for (ResourceLocation id : com.dwurdy.lifepath.ability.AbilityEngine
				.ownedAbilities(data)) {
			var def = LifepathContent.abilities().get(id);
			boolean active = def != null && def.trigger().kind()
					== com.dwurdy.lifepath.content.AbilityDefinition
							.Kind.ACTIVE;
			abilities.put(id.toString(), new IdentitySummaryPayload.AbilityEntry(
					id.toString(), displayNameComponent(id), iconRef(id), active,
					keyedText(id, "ability", "description",
							def != null ? def.description().orElse("") : "")));
		}
		List<IdentitySummaryPayload.ResourceDisplay> resourceDisplays =
				new ArrayList<>();
		java.util.Set<ResourceLocation> resourceIds = new java.util.LinkedHashSet<>(
				data.resources().keySet());
		if (species != null) {
			resourceIds.addAll(species.resources());
		}
		for (ResourceLocation rid : resourceIds) {
			var rdef = LifepathContent.resources().get(rid);
			if (rdef == null) {
				continue;
			}
			List<Component> bandNames = new ArrayList<>();
			for (int i = 0; i < rdef.bands().size(); i++) {
				bandNames.add(keyedText(rid, "resource", "band." + i,
						rdef.bands().get(i).name()));
			}
			int restBand = -1;
			for (int i = 0; i < rdef.bands().size(); i++) {
				var b = rdef.bands().get(i);
				if (rdef.defaultValue() >= b.lo() && rdef.defaultValue() <= b.hi()) {
					restBand = i;
					break;
				}
			}
			resourceDisplays.add(new IdentitySummaryPayload.ResourceDisplay(
					rid.toString(), keyedText(rid, "resource", "name", rdef.displayName()),
					rdef.defaultValue(),
					restBand, bandNames,
					rdef.icon().map(ResourceLocation::toString).orElse("")));
		}

		// M-1: morph disguise view — the resolved entity_type is what lets the
		// client render the form without owning the morph_form registry. A
		// deleted-form edge (reload mid-session) degrades to the id path with
		// no entity — the client renders the plain player, never crashes.
		IdentitySummaryPayload.MorphView morph = IdentitySummaryPayload.MorphView.EMPTY;
		PlayerCharacterData.MorphState morphState = data.morph();
		if (morphState != null) {
			var form = LifepathContent.morphForms().get(morphState.formId());
			morph = new IdentitySummaryPayload.MorphView(
					morphState.formId().toString(),
					form != null ? form.entityType().toString() : "",
					keyedText(morphState.formId(), "morph_form", "name",
							form != null ? form.displayName() : morphState.formId().getPath()),
					form != null ? form.icon().map(ResourceLocation::toString).orElse("") : "",
					morphState.active());
		}

		return new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore(speciesId, speciesName,
						speciesDesc, speciesIcon, specId, specName, specIcon),
				focus, sections, abilities, resourceDisplays, morph);
	}

	/**
	 * Display name for any content id: ability → {@code displayName}, skill →
	 * {@code displayName}, else the id path. Never throws on unknown content.
	 */
	public static String displayName(ResourceLocation id) {
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
		// Keep the domain ordering identical to domainOf()/iconRef() —
		// morph_form resolves last so a dual-registered id picks one def
		// consistently for name, domain key, and icon alike.
		var form = LifepathContent.morphForms().get(id);
		if (form != null) {
			return form.displayName();
		}
		return id.getPath();
	}

	/**
	 * Icon texture ref for any content id (M12-1): the def's {@code icon},
	 * resolved through the same domain chain as {@link #displayName} so a
	 * name and its icon can never disagree about which def they came from.
	 * Empty when the def declares none or the id resolves to nothing.
	 */
	public static String iconRef(ResourceLocation id) {
		var ability = LifepathContent.abilities().get(id);
		if (ability != null) {
			return ability.icon().map(ResourceLocation::toString).orElse("");
		}
		var skill = LifepathContent.skills().get(id);
		if (skill != null) {
			return skill.icon().map(ResourceLocation::toString).orElse("");
		}
		var condition = LifepathContent.conditions().get(id);
		if (condition != null) {
			return condition.icon().map(ResourceLocation::toString).orElse("");
		}
		var attunement = LifepathContent.attunements().get(id);
		if (attunement != null) {
			return attunement.icon().map(ResourceLocation::toString).orElse("");
		}
		// Same terminal as displayName(): specialization/resource icons ride
		// their own payload fields (IdentityCore, ResourceDisplay), never
		// through entry() — stopping here keeps the name/icon chains equal.
		var species = LifepathContent.species().get(id);
		if (species != null) {
			return species.icon().map(ResourceLocation::toString).orElse("");
		}
		var form = LifepathContent.morphForms().get(id);
		if (form != null) {
			return form.icon().map(ResourceLocation::toString).orElse("");
		}
		return "";
	}

	/** One displayable reference: id + localizable name + icon ref ("" none). */
	public static IdentitySummaryPayload.Entry entry(ResourceLocation id) {
		return new IdentitySummaryPayload.Entry(id.toString(),
				displayNameComponent(id), iconRef(id));
	}

	private static List<IdentitySummaryPayload.Entry> entriesOf(List<ResourceLocation> ids) {
		List<IdentitySummaryPayload.Entry> entries = new ArrayList<>(ids.size());
		for (ResourceLocation id : ids) {
			entries.add(entry(id));
		}
		return entries;
	}
}
