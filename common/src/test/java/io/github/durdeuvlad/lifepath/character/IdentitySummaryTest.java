package io.github.durdeuvlad.lifepath.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M6-1: {@link IdentitySummary} resolves ids to display strings server-side —
 * the client's character screen renders exactly these. Missing definitions
 * degrade to the id path; unset fields stay empty.
 */
class IdentitySummaryTest {
	private static final Path DATA = Path.of("src/main/resources/data/lifepath");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
		LifepathContent.resources().clear();
		LifepathContent.abilities().clear();
		LifepathContent.morphForms().clear();
	}

	@AfterEach
	void tearDown() {
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
		LifepathContent.resources().clear();
		LifepathContent.abilities().clear();
		LifepathContent.morphForms().clear();
	}

	private static SpeciesDefinition species(String name) throws Exception {
		return species(name, null);
	}

	private static SpeciesDefinition species(String name, String icon)
			throws Exception {
		var json = JsonParser.parseString(Files.readString(
				DATA.resolve("species/" + name + ".json"))).getAsJsonObject();
		if (icon != null) {
			json.addProperty("icon", icon);
		}
		var file = SpeciesDefinition.SpeciesDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, json)
				.result().orElseThrow();
		return SpeciesDefinition.fromFile(LifepathMod.id(name), file);
	}

	@Test
	void emptyCharacterGivesEmptyIdentity() {
		IdentitySummaryPayload p = IdentitySummary.build(data);
		assertEquals("", p.identity().speciesName().getString());
		assertEquals("", p.identity().specName().getString());
		assertTrue(p.specFocus().isEmpty());
		assertTrue(p.sections().get(IdentitySummary.SECTION_TRAITS).isEmpty());
	}

	@Test
	void resolvesSpeciesAndSpecialization() throws Exception {
		LifepathContent.species().register(LifepathMod.id("sylvian"),
				species("sylvian"));
		var specFile = SpecializationDefinition.SpecializationDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(
						DATA.resolve("specialization/miner.json"))))
				.result().orElseThrow();
		LifepathContent.specializations().register(LifepathMod.id("miner"),
				SpecializationDefinition.fromFile(LifepathMod.id("miner"), specFile));
		// Focus resolution needs the skill defs to name-check against.
		var skillFile = io.github.durdeuvlad.lifepath.content.SkillDefinition
				.SkillDefinitionFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(
								DATA.resolve("skill/mining.json"))))
				.result().orElseThrow();
		LifepathContent.skills().register(LifepathMod.id("mining"),
				io.github.durdeuvlad.lifepath.content.SkillDefinition.fromFile(
						LifepathMod.id("mining"), skillFile));

		data.setSpeciesId(LifepathMod.id("sylvian"));
		data.setSpecializationId(LifepathMod.id("miner"));
		IdentitySummaryPayload p = IdentitySummary.build(data);

		assertEquals("Sylvian", p.identity().speciesName().getString());
		assertTrue(p.identity().speciesDescription().getString().contains("Sylvian")
						|| !p.identity().speciesDescription().getString().isEmpty(),
				"identity text flows to the client");
		assertEquals("Miner", p.identity().specName().getString());
		// startingSkills resolves through the skill registry → display names.
		assertTrue(p.specFocus().stream().anyMatch(e -> e.name().getString().equals("Mining")),
				"focus resolves skill display names: " + p.specFocus());
	}

	@Test
	void deletedDefinitionsDegradeToIdPath() {
		// Species set to an id whose definition is absent (deleted datapack).
		data.setSpeciesId(LifepathMod.id("phantom"));
		data.setSpecializationId(LifepathMod.id("archmage"));
		IdentitySummaryPayload p = IdentitySummary.build(data);
		assertEquals("phantom", p.identity().speciesName().getString());
		assertEquals("archmage", p.identity().specName().getString());
		assertTrue(p.specFocus().isEmpty());
	}

	@Test
	void traitIdsResolveOrDegrade() {
		data.addId(PlayerCharacterData.ListKind.TRAITS,
				LifepathMod.id("undead_death_sight"));
		var abilityFile = io.github.durdeuvlad.lifepath.content.AbilityDefinition
				.AbilityFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(uncheckedRead(
								"ability/undead_death_sight.json")))
				.result().orElseThrow();
		LifepathContent.abilities().register(LifepathMod.id("undead_death_sight"),
				io.github.durdeuvlad.lifepath.content.AbilityDefinition
						.fromFile(LifepathMod.id("undead_death_sight"), abilityFile));
		data.addId(PlayerCharacterData.ListKind.TRAITS,
				LifepathMod.id("missing_trait"));

		IdentitySummaryPayload p = IdentitySummary.build(data);
		var traits = p.sections().get(IdentitySummary.SECTION_TRAITS);
		assertEquals("Death Sight", traits.get(0).name().getString());
		assertEquals("lifepath:undead_death_sight", traits.get(0).id());
		assertEquals("missing_trait", traits.get(1).name().getString());
	}

	@Test
	void resourceDisplaysAndAbilityNamesFlow() {
		// M6-3: the HUD needs static resource display info + owned-ability
		// names resolved server-side.
		LifepathContent.resources().register(LifepathMod.id("temperature"),
				new io.github.durdeuvlad.lifepath.content.ResourceDefinition(
						LifepathMod.id("temperature"), "Temperature", 0, 100, 50,
						0, java.util.List.of(
								new io.github.durdeuvlad.lifepath.content
										.ResourceDefinition.Band("Cold", 0, 25,
												java.util.List.of(),
												java.util.List.of()),
								new io.github.durdeuvlad.lifepath.content
										.ResourceDefinition.Band("Temperate", 26,
												100, java.util.List.of(),
												java.util.List.of()))));
		data.setResource(LifepathMod.id("temperature"),
				new PlayerCharacterData.ResourceState(80, 0, 100));
		data.addId(PlayerCharacterData.ListKind.TRAITS,
				LifepathMod.id("undead_death_sight"));
		var abilityFile = io.github.durdeuvlad.lifepath.content.AbilityDefinition
				.AbilityFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(uncheckedRead(
								"ability/undead_death_sight.json")))
				.result().orElseThrow();
		LifepathContent.abilities().register(LifepathMod.id("undead_death_sight"),
				io.github.durdeuvlad.lifepath.content.AbilityDefinition
						.fromFile(LifepathMod.id("undead_death_sight"), abilityFile));

		IdentitySummaryPayload p = IdentitySummary.build(data);
		assertEquals(1, p.resourceDisplays().size());
		var rd = p.resourceDisplays().get(0);
		assertEquals("Temperature", rd.name().getString());
		assertEquals(50, rd.defaultValue(), 0.001);
		assertEquals(java.util.List.of("Cold", "Temperate"),
				rd.bandNames().stream().map(c -> c.getString()).toList());
		assertEquals(1, rd.restBandIndex()); // default 50 sits in [26,100]
		assertEquals("Death Sight",
				p.abilities().get("lifepath:undead_death_sight").name().getString());
	}

	@Test
	void iconRefsFlowIntoEntriesAndCore() throws Exception {
		// M12-2: every displayable reference carries its def's icon ref so
		// the client can render icons without touching server registries.
		LifepathContent.species().register(LifepathMod.id("sylvian"),
				species("sylvian", "species/sylvian"));
		data.setSpeciesId(LifepathMod.id("sylvian"));

		var abilityJson = JsonParser.parseString(uncheckedRead(
				"ability/undead_death_sight.json")).getAsJsonObject();
		abilityJson.addProperty("icon", "ability/death_sight");
		var abilityFile = io.github.durdeuvlad.lifepath.content.AbilityDefinition
				.AbilityFile.CODEC.parse(JsonOps.INSTANCE, abilityJson)
				.result().orElseThrow();
		LifepathContent.abilities().register(LifepathMod.id("undead_death_sight"),
				io.github.durdeuvlad.lifepath.content.AbilityDefinition
						.fromFile(LifepathMod.id("undead_death_sight"), abilityFile));
		data.addId(PlayerCharacterData.ListKind.TRAITS,
				LifepathMod.id("undead_death_sight"));

		IdentitySummaryPayload p = IdentitySummary.build(data);
		assertEquals("lifepath:textures/gui/species/sylvian.png",
				p.identity().speciesIcon());
		// Owned abilities (traits) surface in the abilities map with icon.
		var ability = p.abilities().get("lifepath:undead_death_sight");
		assertEquals("lifepath:textures/gui/ability/death_sight.png",
				ability.icon());
		// The same reference in a section row carries the same icon.
		var trait = p.sections().get(IdentitySummary.SECTION_TRAITS).get(0);
		assertEquals(ability.icon(), trait.icon());
		// A def without an icon field degrades to "" (no placeholder
		// resolution on the wire — that is the client's job).
		assertEquals("", p.identity().specIcon());
	}

	/**
	 * M-1 (morph): a character with morph state carries the resolved
	 * {@code MorphView} — form id, entity type, display name, icon, active —
	 * so the client can render the disguise without the morph_form registry.
	 */
	@Test
	void morphStateResolvesIntoMorphView() {
		var formFile = io.github.durdeuvlad.lifepath.content.MorphFormDefinition.MorphFormFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{
							"entity_type": "minecraft:fox",
							"display_name": "Fox",
							"icon": "morph/fox",
							"stats": {"minecraft:generic.max_health": 10.0}
						}
						"""))
				.result().orElseThrow();
		LifepathContent.morphForms().register(LifepathMod.id("fox"),
				io.github.durdeuvlad.lifepath.content.MorphFormDefinition
						.fromFile(LifepathMod.id("fox"), formFile));
		data.setMorph(new PlayerCharacterData.MorphState(
				LifepathMod.id("fox"), true, 42L));

		IdentitySummaryPayload p = IdentitySummary.build(data);

		assertEquals("lifepath:fox", p.morph().formId());
		assertEquals("minecraft:fox", p.morph().entityType());
		assertEquals("Fox", p.morph().name().getString());
		assertEquals("lifepath:textures/gui/morph/fox.png", p.morph().icon());
		assertTrue(p.morph().active());
	}

	@Test
	void morphViewDegradesOnDeletedForm() {
		// Form id set but def absent (reload removed it mid-session) — the
		// payload degrades to the id path with no entity_type so the client
		// renders the plain player instead of crashing.
		data.setMorph(new PlayerCharacterData.MorphState(
				LifepathMod.id("gone"), true, 42L));

		IdentitySummaryPayload p = IdentitySummary.build(data);

		assertEquals("lifepath:gone", p.morph().formId());
		assertEquals("", p.morph().entityType());
		assertEquals("gone", p.morph().name().getString());
		assertTrue(p.morph().active());
	}

	@Test
	void noMorphGivesEmptyView() {
		IdentitySummaryPayload p = IdentitySummary.build(data);
		assertEquals(IdentitySummaryPayload.MorphView.EMPTY, p.morph());
	}

	private static String uncheckedRead(String rel) {
		try {
			return Files.readString(DATA.resolve(rel));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
