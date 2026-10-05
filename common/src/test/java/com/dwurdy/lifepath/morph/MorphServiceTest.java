package com.dwurdy.lifepath.morph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityVocabulary;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.MorphFormDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M-3 morph lifecycle (docs/MORPH_FEATURE.md): data-path coverage for the
 * proportional HP carry, the forced-demorph overflow math, and toggle gating.
 * Entity-touching paths (attribute modifiers, health writes, dimensions) are
 * verified in-game like every player-dependent surface.
 */
class MorphServiceTest {
	private static final Path DATA = Path.of("src/main/resources/data/lifepath");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		data = PlayerCharacterData.createDefault();
		LifepathContent.morphForms().clear();
	}

	@AfterEach
	void tearDown() {
		AbilityVocabulary.resetForTests();
		LifepathContent.morphForms().clear();
	}

	private static MorphFormDefinition form(String id, double maxHealth) {
		var file = new MorphFormDefinition.MorphFormFile(
				ResourceLocation.parse("minecraft:fox"), "Fox",
				Optional.empty(), Optional.empty(),
				java.util.Map.of(
						ResourceLocation.parse("minecraft:generic.max_health"), maxHealth));
		return MorphFormDefinition.fromFile(LifepathMod.id(id), file);
	}

	@Test
	void carriedHealthIsProportionalBothWays() {
		// 15/20 human → 7.5/10 fox; 7.5/10 fox → 15/20 human.
		assertEquals(7.5f, MorphService.carriedHealth(15f, 20f, 10f), 1.0e-6);
		assertEquals(15f, MorphService.carriedHealth(7.5f, 10f, 20f), 1.0e-6);
		assertEquals(10f, MorphService.carriedHealth(20f, 20f, 10f), 1.0e-6);
		// A zero/negative source max fails safe to the target max.
		assertEquals(20f, MorphService.carriedHealth(5f, 0f, 20f), 1.0e-6);
	}

	@Test
	void forcedCarryLandsOverflowScaled() {
		// Fox bar 10, human bar 20: a 12-damage hit at 10 hp overflows 2 —
		// scaled to the human bar that's 4 → land at 16/20.
		assertEquals(16f, MorphService.forcedCarryHealth(10f, -2f, 10f, 20f), 1.0e-6);
		// Partial bar: 8/10 fox, 9 damage → overflow 1 → 2 human → 16-2=14.
		assertEquals(14f, MorphService.forcedCarryHealth(8f, -1f, 10f, 20f), 1.0e-6);
		// Exact-lethal: no overflow — the form absorbed the hit, human lands
		// at the carried ratio (full here since the form was full).
		assertEquals(20f, MorphService.forcedCarryHealth(10f, 0f, 10f, 20f), 1.0e-6);
	}

	@Test
	void forcedCarryPastHumanMaxStillKills() {
		// Overflow bigger than the human bar: 30 damage on a 10hp fox →
		// overflow 20 → carried 40 against 20 — real death proceeds.
		assertTrue(MorphService.forcedCarryHealth(10f, -20f, 10f, 20f) <= 0f);
		// Zero-boundary: lands exactly 0 → death.
		assertEquals(0f, MorphService.forcedCarryHealth(10f, -10f, 10f, 20f), 1.0e-6);
	}

	@Test
	void toggleCheckGatesOnFormPresence() {
		assertEquals(MorphService.ToggleCheck.NO_FORM, MorphService.check(data));

		data.setMorph(new PlayerCharacterData.MorphState(LifepathMod.id("fox"), false, 0L));
		assertEquals(MorphService.ToggleCheck.FORM_MISSING, MorphService.check(data));

		LifepathContent.morphForms().register(LifepathMod.id("fox"), form("fox", 10.0));
		assertEquals(MorphService.ToggleCheck.READY, MorphService.check(data));
	}

	@Test
	void morphModifierIdsAreNamespacedPerAttribute() {
		assertEquals("lifepath:morph/minecraft_generic.max_health",
				MorphService.modifierId(
						ResourceLocation.parse("minecraft:generic.max_health")).toString());
	}

	@Test
	void morphToggleActionIsRegistered() {
		assertNotNull(AbilityVocabulary.action(MorphService.TOGGLE_ACTION),
				"morph_toggle action must register before ability-file validation");
	}

	/** The shipped anima_morph ability decodes, targets self, carries cooldown. */
	@Test
	void shippedMorphAbilityDecodes() throws Exception {
		var json = JsonParser.parseString(Files.readString(
				DATA.resolve("ability/anima_morph.json")));
		var file = AbilityDefinition.AbilityFile.CODEC.parse(JsonOps.INSTANCE, json)
				.result().orElseThrow();
		var def = AbilityDefinition.fromFile(LifepathMod.id("anima_morph"), file);

		assertEquals(AbilityDefinition.Kind.ACTIVE, def.trigger().kind());
		assertTrue(def.actions().stream()
				.anyMatch(n -> n.type().equals(MorphService.TOGGLE_ACTION)));
		assertTrue(def.cooldown().isPresent() && def.cooldown().get().seconds() > 0,
				"re-morph cooldown must ride the ability's cooldown field");
		assertTrue(AbilityVocabulary.unknownNodeTypes(def).isEmpty(),
				"shipped ability must resolve against the live vocabulary");
	}

	/** The anima species grants anima_morph as an active ability. */
	@Test
	void animaSpeciesGrantsMorphAbility() throws Exception {
		var json = JsonParser.parseString(Files.readString(
				DATA.resolve("species/anima.json")));
		var file = com.dwurdy.lifepath.content.SpeciesDefinition
				.SpeciesDefinitionFile.CODEC.parse(JsonOps.INSTANCE, json)
				.result().orElseThrow();
		assertTrue(file.activeAbilities().contains(LifepathMod.id("anima_morph")),
				"anima species must grant lifepath:anima_morph");
	}
}
