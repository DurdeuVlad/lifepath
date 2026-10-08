package com.dwurdy.lifepath.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityEngine;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.specialization.SpecializationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M5-1: the shipped {@code data/lifepath/species/*.json} files must parse, and
 * {@code lifepath:human} pins the baseline contract — open selection, normal
 * visibility, zero innate mechanics — while flowing through the full pipeline.
 */
class ShippedSpeciesDefinitionsTest {
	private static final Path SPECIES_DIR =
			Path.of("src/main/resources/data/lifepath/species");
	private static final ResourceLocation HUMAN = LifepathMod.id("human");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		ActivityDispatcher.resetForTests();
		com.dwurdy.lifepath.ability.AbilityVocabulary.resetForTests();
		com.dwurdy.lifepath.ability.AbilityVocabulary.init();
		// ContentRegistry.register rejects duplicates — clear defensively
		// because sibling test classes have demonstrated registry leaks.
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		com.dwurdy.lifepath.ability.AbilityVocabulary.resetForTests();
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
	}

	private static SpeciesDefinition loadShipped(String name) throws Exception {
		var parsed = SpeciesDefinition.SpeciesDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(
						Files.readString(SPECIES_DIR.resolve(name + ".json"))));
		var file = parsed.result().orElseThrow(() -> new AssertionError(
				name + " must parse: " + parsed.error().orElse(null)));
		return SpeciesDefinition.fromFile(LifepathMod.id(name), file);
	}

	@Test
	void everyShippedSpeciesFileParses() throws Exception {
		try (Stream<Path> files = Files.list(SPECIES_DIR)) {
			var names = files.filter(p -> p.toString().endsWith(".json"))
					.map(p -> p.getFileName().toString().replace(".json", "")).toList();
			assertTrue(names.contains("human"), "human.json must ship");
			for (String name : names) {
				loadShipped(name); // throws on malformed — files fail at load
			}
		}
	}

	@Test
	void humanIsTheBaselineControl() throws Exception {
		SpeciesDefinition human = loadShipped("human");
		assertEquals(SpeciesDefinition.Visibility.NORMAL, human.visibility());
		assertEquals(SpeciesDefinition.Selection.OPEN, human.selection());
		// Beta 8 balance pass: human is no longer the empty control — it owns
		// the "earned survivor" micro-kit (low-health surge + standing luck).
		assertEquals(List.of(LifepathMod.id("human_resolve"),
				LifepathMod.id("human_fortune")), human.passiveAbilities());
		assertTrue(human.activeAbilities().isEmpty(), "no innate actives");
		assertTrue(human.resources().isEmpty(), "no innate resources");
		assertTrue(human.minAptitudes().isEmpty(), "no aptitude floors");
		assertTrue(human.dietRules().isEmpty() && human.mobDispositions().isEmpty());
		assertFalse(human.displayName().isBlank());
		assertTrue(human.description().isPresent()
						&& !human.description().get().isBlank(),
				"identity text required for selection UX");
	}

	@Test
	void speciesScaleFieldIsOptionalAndDecodes() throws Exception {
		// Compat A5: scale is opt-in — species without it parse with empty,
		// dwarf/goliath pin the shipped small/large ends.
		SpeciesDefinition human = loadShipped("human");
		assertTrue(human.scale().isEmpty(), "unscaled species carry no field");
		assertEquals(0.7, loadShipped("dwarf").scale().orElseThrow(), 1e-6);
		assertEquals(1.4, loadShipped("goliath").scale().orElseThrow(), 1e-6);
	}

	@Test
	void humanFlowsThroughTheFullPipeline() throws Exception {
		// Species set → owned abilities is just the empty lists.
		SpeciesDefinition human = loadShipped("human");
		LifepathContent.species().register(HUMAN, human);
		data.setSpeciesId(HUMAN);
		assertEquals(Set.of(LifepathMod.id("human_resolve"),
						LifepathMod.id("human_fortune")),
				AbilityEngine.ownedAbilities(data),
				"human owns its micro-kit");

		// Ability-less species contributes nothing to the sweep/event paths
		// (their no-op coverage lives in the package-private test seams).
		assertTrue(data.resources().isEmpty(), "no species resources materialize");

		// Specialization still applies — human is not a special case.
		ResourceLocation miner = LifepathMod.id("miner");
		var specFile = SpecializationDefinition.SpecializationDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(
						Path.of("src/main/resources/data/lifepath/specialization/miner.json"))))
				.result().orElseThrow();
		LifepathContent.specializations().register(miner,
				SpecializationDefinition.fromFile(miner, specFile));
		assertEquals(SpecializationService.ApplyResult.APPLIED,
				SpecializationService.apply(data, miner));
		assertEquals(miner, data.specializationId());
	}
}
