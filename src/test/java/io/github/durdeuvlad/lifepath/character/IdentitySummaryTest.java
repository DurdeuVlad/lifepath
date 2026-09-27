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
import net.minecraft.util.Identifier;
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
	}

	@AfterEach
	void tearDown() {
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
	}

	private static SpeciesDefinition species(String name) throws Exception {
		var file = SpeciesDefinition.SpeciesDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(
						DATA.resolve("species/" + name + ".json"))))
				.result().orElseThrow();
		return SpeciesDefinition.fromFile(LifepathMod.id(name), file);
	}

	@Test
	void emptyCharacterGivesEmptyIdentity() {
		IdentitySummaryPayload p = IdentitySummary.build(data);
		assertEquals("", p.identity().speciesName());
		assertEquals("", p.identity().specName());
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

		assertEquals("Sylvian", p.identity().speciesName());
		assertTrue(p.identity().speciesDescription().contains("Sylvian")
						|| !p.identity().speciesDescription().isEmpty(),
				"identity text flows to the client");
		assertEquals("Miner", p.identity().specName());
		// startingSkills resolves through the skill registry → display names.
		assertTrue(p.specFocus().contains("Mining"),
				"focus resolves skill display names: " + p.specFocus());
	}

	@Test
	void deletedDefinitionsDegradeToIdPath() {
		// Species set to an id whose definition is absent (deleted datapack).
		data.setSpeciesId(LifepathMod.id("phantom"));
		data.setSpecializationId(LifepathMod.id("archmage"));
		IdentitySummaryPayload p = IdentitySummary.build(data);
		assertEquals("phantom", p.identity().speciesName());
		assertEquals("archmage", p.identity().specName());
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
		assertEquals("Death Sight", traits.get(0));
		assertEquals("missing_trait", traits.get(1));
	}

	private static String uncheckedRead(String rel) {
		try {
			return Files.readString(DATA.resolve(rel));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}
}
