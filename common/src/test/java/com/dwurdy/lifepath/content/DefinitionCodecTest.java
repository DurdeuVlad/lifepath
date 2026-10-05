package com.dwurdy.lifepath.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.skill.Aptitude;
import net.minecraft.resources.ResourceLocation;
import com.dwurdy.lifepath.content.SkillDefinition.Category;
import com.dwurdy.lifepath.content.SpeciesDefinition.Selection;
import com.dwurdy.lifepath.content.SpeciesDefinition.Visibility;
import org.junit.jupiter.api.Test;

class DefinitionCodecTest {

	@Test
	void speciesFileDecodesWithDefaults() {
		var file = SpeciesDefinition.SpeciesDefinitionFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"display_name\": \"Human\"}")).result().orElseThrow();

		assertEquals("Human", file.displayName());
		assertEquals(Visibility.NORMAL, file.visibility());
		assertEquals(Selection.OPEN, file.selection());
		assertTrue(file.passiveAbilities().isEmpty());
		assertTrue(file.minAptitudes().isEmpty());
		assertTrue(file.dietRules().isEmpty());
	}

	@Test
	void speciesFileDecodesFullFields() {
		var file = SpeciesDefinition.SpeciesDefinitionFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{
							"display_name": "Fae",
							"visibility": "hidden",
							"selection": "unlocked",
							"passive_abilities": ["lifepath:fae_grace"],
							"min_aptitudes": {"lifepath:foraging": "a"},
							"resources": ["lifepath:mana"],
							"diet_rules": "lifepath:photosynthetic"
						}
						""")).result().orElseThrow();

		assertEquals(Visibility.HIDDEN, file.visibility());
		assertEquals(Selection.UNLOCKED, file.selection());
		assertEquals(Aptitude.A, file.minAptitudes().get(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging")));
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "photosynthetic"), file.dietRules().orElseThrow());
	}

	@Test
	void specializationFileDecodesModifierMaps() {
		var file = SpecializationDefinition.SpecializationDefinitionFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{
							"display_name": "Scout",
							"starting_skills": {"lifepath:foraging": 3},
							"aptitudes": {"lifepath:foraging": "b"},
							"xp_modifiers": {"lifepath:foraging": 1.25},
							"decay_modifiers": {"lifepath:foraging": 0.5},
							"protected_floors": {"lifepath:foraging": 5},
							"signature": ["lifepath:keen_eye"]
						}
						""")).result().orElseThrow();

		assertEquals(3, file.startingSkills().get(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging")));
		assertEquals(Aptitude.B, file.aptitudes().get(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging")));
		assertEquals(1.25, file.xpModifiers().get(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging")));
		assertEquals(0.5, file.decayModifiers().get(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging")));
		assertEquals(5, file.protectedFloors().get(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging")));
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "keen_eye"), file.signatureRefs().get(0));
	}

	@Test
	void skillFileDecodesMilestonesAndDefaults() {
		var file = SkillDefinition.SkillDefinitionFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{
							"display_name": "Foraging",
							"category": "gathering",
							"milestones": [
								{"level": 10, "description_key": "lifepath.skill.trained_eye", "effects": ["lifepath:e1"]}
							]
						}
						""")).result().orElseThrow();

		assertEquals(Category.GATHERING, file.category());
		assertEquals(100, file.maxLevel());
		assertEquals(1, file.milestones().size());
		assertEquals(10, file.milestones().get(0).level());
		assertEquals("lifepath.skill.trained_eye", file.milestones().get(0).descriptionKey());
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "e1"), file.milestones().get(0).effectRefs().get(0));
		assertTrue(file.levelCurve().isEmpty());
		assertTrue(file.xpSources().isEmpty());
	}

	@Test
	void missingRequiredFieldFailsToDecode() {
		var result = SpeciesDefinition.SpeciesDefinitionFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"visibility\": \"normal\"}"));

		assertTrue(result.error().isPresent());
	}
}
