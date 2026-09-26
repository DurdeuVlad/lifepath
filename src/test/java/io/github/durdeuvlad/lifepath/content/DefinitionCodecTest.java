package io.github.durdeuvlad.lifepath.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.Aptitude;
import io.github.durdeuvlad.lifepath.content.SkillDefinition.Category;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition.Selection;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition.Visibility;
import net.minecraft.util.Identifier;
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
		assertEquals(Aptitude.A, file.minAptitudes().get(Identifier.of("lifepath", "foraging")));
		assertEquals(Identifier.of("lifepath", "photosynthetic"), file.dietRules().orElseThrow());
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

		assertEquals(3, file.startingSkills().get(Identifier.of("lifepath", "foraging")));
		assertEquals(Aptitude.B, file.aptitudes().get(Identifier.of("lifepath", "foraging")));
		assertEquals(1.25, file.xpModifiers().get(Identifier.of("lifepath", "foraging")));
		assertEquals(0.5, file.decayModifiers().get(Identifier.of("lifepath", "foraging")));
		assertEquals(5, file.protectedFloors().get(Identifier.of("lifepath", "foraging")));
		assertEquals(Identifier.of("lifepath", "keen_eye"), file.signatureRefs().get(0));
	}

	@Test
	void skillFileDecodesMilestonesAndDefaults() {
		var file = SkillDefinition.SkillDefinitionFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{
							"display_name": "Foraging",
							"category": "gathering",
							"milestones": [
								{"level": 10, "description": "Trained eye", "effects": ["lifepath:e1"]}
							]
						}
						""")).result().orElseThrow();

		assertEquals(Category.GATHERING, file.category());
		assertEquals(100, file.maxLevel());
		assertEquals(1, file.milestones().size());
		assertEquals(10, file.milestones().get(0).level());
		assertEquals(Identifier.of("lifepath", "e1"), file.milestones().get(0).effectRefs().get(0));
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
