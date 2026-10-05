package com.dwurdy.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.ResourceDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.resource.ResourceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M5-3 Iceborn: the temperature meter is a plain {@link ResourceDefinition}
 * and every driver is a generic conditioned {@code modify_resource} — the test
 * pins the shipped files through the real decode path plus the species wiring.
 */
class IcebornSpeciesTest {
	private static final Path ABILITY_DIR =
			Path.of("src/main/resources/data/lifepath/ability");
	private static final Path SPECIES_DIR =
			Path.of("src/main/resources/data/lifepath/species");
	private static final Path RESOURCE_DIR =
			Path.of("src/main/resources/data/lifepath/resource");
	private static final ResourceLocation TEMPERATURE = LifepathMod.id("temperature");
	private static final ResourceLocation ICEBORN = LifepathMod.id("iceborn");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		LifepathContent.abilities().clear();
		LifepathContent.resources().clear();
		LifepathContent.species().clear();
		try {
			// Mirrors live load order: resources decode before abilities —
			// strict ref-checks on `resource` params need the def present.
			LifepathContent.resources().register(TEMPERATURE, loadTemperature());
		} catch (Exception e) {
			throw new AssertionError("temperature.json must decode", e);
		}
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		LifepathContent.abilities().clear();
		LifepathContent.resources().clear();
		LifepathContent.species().clear();
	}

	private static ResourceDefinition loadTemperature() throws Exception {
		var parsed = ResourceDefinition.ResourceFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString(
						Files.readString(RESOURCE_DIR.resolve("temperature.json"))))
				.result().orElseThrow(() -> new AssertionError("temperature failed to parse"));
		return ResourceDefinition.fromFile(TEMPERATURE, parsed);
	}

	private static List<AbilityDefinition> loadIcebornAbilities() throws Exception {
		try (Stream<Path> files = Files.list(ABILITY_DIR)) {
			return files.filter(p -> p.getFileName().toString().startsWith("iceborn_"))
					.map(f -> {
						try {
							var parsed = AbilityDefinition.AbilityFile.CODEC.parse(
											JsonOps.INSTANCE, JsonParser.parseString(
													Files.readString(f)))
									.result().orElseThrow(() -> new AssertionError(
											f + " failed to parse"));
							ResourceLocation id = LifepathMod.id(f.getFileName().toString()
									.replace(".json", ""));
							return LifepathContent.decodeAbility(id, parsed);
						} catch (Exception e) {
							throw new AssertionError(f + " failed", e);
						}
					}).toList();
		}
	}

	@Test
	void temperatureResourceMatchesSpecBands() throws Exception {
		ResourceDefinition def = loadTemperature();
		assertEquals(0.0, def.min());
		assertEquals(100.0, def.max());
		assertEquals(50.0, def.defaultValue());
		assertEquals(3, def.bands().size());
		// Spec band edges: [0,25] adapted, (26,70) normal — a gap, no band —
		// [71,85] slowness, [86,99] weakness+slowness, 100 = no band (the
		// periodic-damage ability owns the overheat state).
		assertEquals(0, ResourceDefinition.bandOf(def, 25));
		assertEquals(-1, ResourceDefinition.bandOf(def, 50));
		assertEquals(1, ResourceDefinition.bandOf(def, 80));
		assertEquals(2, ResourceDefinition.bandOf(def, 90));
		assertEquals(-1, ResourceDefinition.bandOf(def, 100));
	}

	@Test
	void icebornAbilityFilesAllValidate() throws Exception {
		List<AbilityDefinition> defs = loadIcebornAbilities();
		assertEquals(8, defs.size(), "iceborn ships 8 passives (frostbite replaced frost walk)");
		for (var def : defs) {
			assertTrue(AbilityVocabulary.unknownNodeTypes(def).isEmpty(),
					def.id() + " uses unknown spec nodes");
		}
		// Every environmental driver is generic composition: modify_resource
		// (drivers), damage (overheat), freeze_ticks/apply_effect (frostbite).
		Set<ResourceLocation> allowedActions = Set.of(LifepathMod.id("modify_resource"),
				LifepathMod.id("damage"), LifepathMod.id("freeze_ticks"),
				LifepathMod.id("apply_effect"), LifepathMod.id("spawn_particle"));
		for (var def : defs) {
			for (var action : def.actions()) {
				assertTrue(allowedActions.contains(action.type()),
						def.id() + " uses non-generic action " + action.type());
			}
		}
	}

	@Test
	void icebornSpeciesWiresAllRefs() throws Exception {
		var parsed = SpeciesDefinition.SpeciesDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(
						Files.readString(SPECIES_DIR.resolve("iceborn.json"))))
				.result().orElseThrow(() -> new AssertionError("iceborn failed to parse"));
		SpeciesDefinition def = SpeciesDefinition.fromFile(ICEBORN, parsed);
		assertEquals(List.of(TEMPERATURE), def.resources());
		assertEquals(8, def.passiveAbilities().size());
		assertTrue(def.passiveAbilities().contains(LifepathMod.id("iceborn_frostbite")),
				"frostbite replaces frost walk as the combat passive");
		assertTrue(def.activeAbilities().isEmpty(),
				"frost walk is gone — iceborn ships no actives");
		// Every referenced ability ships as a real file.
		var shipped = loadIcebornAbilities().stream().map(AbilityDefinition::id)
				.collect(Collectors.toSet());
		assertTrue(shipped.containsAll(def.passiveAbilities()),
				"passive refs must resolve to shipped files");
		assertTrue(shipped.containsAll(def.activeAbilities()));
	}

	@Test
	void temperatureMaterializesAndBandTransitionsFire() throws Exception {
		ResourceDefinition def = loadTemperature();
		// Declared-but-unmaterialized reads as default 50.
		assertEquals(50.0, ResourceService.current(data, TEMPERATURE));
		// A driver-shaped mutation materializes the meter and crosses bands.
		ResourceService.modify(data, null, TEMPERATURE, 40.0, 0L);
		assertEquals(90.0, ResourceService.current(data, TEMPERATURE));
		var stored = data.resources().get(TEMPERATURE);
		assertTrue(stored != null
						&& ResourceDefinition.bandOf(def, stored.current()) == 2,
				"stored state should sit in the weakness band");
		// Cooling back down restores the normal gap.
		ResourceService.modify(data, null, TEMPERATURE, -60.0, 1L);
		assertEquals(30.0, ResourceService.current(data, TEMPERATURE));
	}
}
