package com.dwurdy.lifepath.encumbrance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.config.ConfigSpec;
import com.dwurdy.lifepath.config.LifepathConfig;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M9-3: capacity math + modifier composition on plain character data. */
class EncumbranceServiceTest {

	@TempDir
	Path configDir;

	@BeforeEach
	void setUp() {
		LifepathConfig.resetForTests();
		LifepathConfig.define(EncumbranceService.CONFIG, ConfigSpec.builder()
				.define("enabled", true, "")
				.define("capacity", 200.0, "")
				.define("default_item_weight", 1.0, "")
				.define("container_contents_factor", 0.75, "")
				.define("scan_interval_ticks", 40, "")
				.build());
		LifepathConfig.loadAll(configDir);
	}

	@AfterEach
	void tearDown() {
		LifepathConfig.resetForTests();
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
	}

	@Test
	void capacityDefaultsToConfigBase() {
		assertEquals(200.0, EncumbranceService.capacityOf(PlayerCharacterData.createDefault()));
	}

	@Test
	void speciesAndSpecMultipliersCompose() {
		ResourceLocation spId = LifepathMod.id("pack_mule");
		ResourceLocation specId = LifepathMod.id("hauler");
		LifepathContent.species().register(spId, new SpeciesDefinition(
				spId, "Mule", Optional.empty(), SpeciesDefinition.Visibility.NORMAL,
				SpeciesDefinition.Selection.OPEN, List.of(), List.of(), Map.of(),
				List.of(), Optional.empty(), Optional.empty(), 2.0));
		LifepathContent.specializations().register(specId, new SpecializationDefinition(
				specId, "Hauler", Map.of(), Map.of(), Map.of(), Map.of(),
				Map.of(), List.of(), 1.5));

		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertEquals(200.0, EncumbranceService.capacityOf(data));
		data.setSpeciesId(spId);
		assertEquals(400.0, EncumbranceService.capacityOf(data));
		data.setSpecializationId(specId);
		assertEquals(600.0, EncumbranceService.capacityOf(data));
	}

	@Test
	void missingDefsDegradeToBaseCapacity() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(LifepathMod.id("unloaded_species"));
		data.setSpecializationId(LifepathMod.id("unloaded_spec"));
		assertEquals(200.0, EncumbranceService.capacityOf(data));
	}

	@Test
	void percentClampsAndZeroCapacityFailsHeavy() {
		assertEquals(0.0, EncumbranceService.toPercent(0, 200));
		assertEquals(50.0, EncumbranceService.toPercent(100, 200));
		assertEquals(100.0, EncumbranceService.toPercent(500, 200));
		assertEquals(100.0, EncumbranceService.toPercent(10, 0));
	}

	@AfterEach
	void clearWeights() {
		LifepathContent.itemWeights().clear();
	}

	@Test
	void contentsFactorDefaultsToConfig() {
		// No @contents entries anywhere ⇒ the config default applies.
		assertEquals(0.75, EncumbranceService.contentsFactorOf(
				LifepathMod.id("sack"), tag -> false));
	}

	@Test
	void contentsFactorExactEntryWinsOverTag() {
		ResourceLocation sack = LifepathMod.id("sack");
		ResourceLocation bagTag = LifepathMod.id("bags");
		LifepathContent.itemWeights().register(LifepathMod.id("pack"),
				Map.of(sack + "@contents", 0.5,
						"#" + bagTag + "@contents", 0.9));

		// Exact wins even when a tag would also match.
		assertEquals(0.5, EncumbranceService.contentsFactorOf(sack,
				tag -> tag.equals(bagTag)));
		// Tag entry applies when no exact key exists.
		assertEquals(0.9, EncumbranceService.contentsFactorOf(
				LifepathMod.id("other_bag"), tag -> tag.equals(bagTag)));
		// Non-matching tags fall back to config.
		assertEquals(0.75, EncumbranceService.contentsFactorOf(
				LifepathMod.id("other_bag"), tag -> false));
	}
}
