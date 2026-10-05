package com.dwurdy.lifepath.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LifepathContentTest {

	@AfterEach
	void reset() {
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
		LifepathContent.skills().clear();
		LifepathContent.abilities().clear();
		LifepathContent.morphForms().clear();
		LifepathContent.validateAll();
	}

	@Test
	void registerAllLoadsGoodEntriesAndSkipsMalformed() {
		Map<ResourceLocation, com.google.gson.JsonElement> files = new LinkedHashMap<>();
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", "a"), JsonParser.parseString("{\"display_name\": \"A\"}"));
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", "bad"), JsonParser.parseString("{\"nope\": true}"));
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", "bad_enum"),
				JsonParser.parseString("{\"display_name\": \"X\", \"visibility\": \"bogus\"}"));
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", "b"), JsonParser.parseString("{\"display_name\": \"B\"}"));

		int loaded = LifepathContent.registerAll("species", files,
				SpeciesDefinition.SpeciesDefinitionFile.CODEC, SpeciesDefinition::fromFile,
				LifepathContent.species());

		assertEquals(2, loaded);
		assertTrue(LifepathContent.species().contains(ResourceLocation.fromNamespaceAndPath("lifepath", "a")));
		assertTrue(LifepathContent.species().contains(ResourceLocation.fromNamespaceAndPath("lifepath", "b")));
		assertFalse(LifepathContent.species().contains(ResourceLocation.fromNamespaceAndPath("lifepath", "bad")));
		assertFalse(LifepathContent.species().contains(ResourceLocation.fromNamespaceAndPath("lifepath", "bad_enum")));
	}

	@Test
	void entryIdMapsFilePathToContentId() {
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "human"),
				LifepathContent.entryId(ResourceLocation.fromNamespaceAndPath("lifepath", "species/human.json"), "species"));
		assertEquals(ResourceLocation.fromNamespaceAndPath("mypack", "deep/dir"),
				LifepathContent.entryId(ResourceLocation.fromNamespaceAndPath("mypack", "skill/deep/dir.json"), "skill"));
	}

	@Test
	void existsAnswersRegisteredDomainsAndPermitsUnregistered() {
		LifepathContent.skills().register(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging"),
				new SkillDefinition(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging"), "Foraging",
						SkillDefinition.Category.GATHERING, 100,
						java.util.Optional.empty(), List.of(),
						List.of(), java.util.Optional.empty()));

		assertTrue(LifepathContent.exists("skill", ResourceLocation.fromNamespaceAndPath("lifepath", "foraging")));
		assertFalse(LifepathContent.exists("skill", ResourceLocation.fromNamespaceAndPath("lifepath", "gone")));
		assertFalse(LifepathContent.exists("species", ResourceLocation.fromNamespaceAndPath("lifepath", "gone")));
		// Domains without a registry yet answer permissively (their milestone hasn't landed);
		// ability landed in M4-1 and now answers strictly.
		assertFalse(LifepathContent.exists("ability", ResourceLocation.fromNamespaceAndPath("lifepath", "anything")));
		assertTrue(LifepathContent.exists("trait", ResourceLocation.fromNamespaceAndPath("lifepath", "anything")));
	}

	@Test
	void validationRecordsOnlyTrulyUnresolvedRefs() {
		ResourceLocation skillId = ResourceLocation.fromNamespaceAndPath("lifepath", "foraging");
		ResourceLocation missingSkill = ResourceLocation.fromNamespaceAndPath("lifepath", "missing_skill");
		LifepathContent.skills().register(skillId,
				new SkillDefinition(skillId, "Foraging", SkillDefinition.Category.GATHERING, 100,
						java.util.Optional.empty(), List.of(),
						List.of(), java.util.Optional.empty()));
		ResourceLocation minAptSkill = ResourceLocation.fromNamespaceAndPath("lifepath", "min_apt_skill");
		LifepathContent.species().register(ResourceLocation.fromNamespaceAndPath("lifepath", "human"),
				new SpeciesDefinition(ResourceLocation.fromNamespaceAndPath("lifepath", "human"), "Human",
						SpeciesDefinition.Visibility.NORMAL, SpeciesDefinition.Selection.OPEN,
						List.of(ResourceLocation.fromNamespaceAndPath("lifepath", "fae_grace")), List.of(),
						Map.of(minAptSkill, com.dwurdy.lifepath.skill.Aptitude.B),
						List.of(ResourceLocation.fromNamespaceAndPath("lifepath", "mana")),
						java.util.Optional.empty(), java.util.Optional.empty()));
		LifepathContent.specializations().register(ResourceLocation.fromNamespaceAndPath("lifepath", "wanderer"),
				new SpecializationDefinition(ResourceLocation.fromNamespaceAndPath("lifepath", "wanderer"), "Wanderer",
						Map.of(skillId, 2, missingSkill, 1), Map.of(), Map.of(), Map.of(), Map.of(),
						List.of()));

		LifepathContent.validateAll();

		var unresolved = LifepathContent.unresolvedReferences();
		// ability ref + resource ref + missing skill + missing min-aptitude skill = 4;
		// the resolved skill ref is absent.
		assertEquals(4, unresolved.size());
		assertTrue(unresolved.stream().noneMatch(u -> u.ref().equals(skillId)));
		assertTrue(unresolved.stream().anyMatch(u -> u.ref().equals(missingSkill)
				&& u.targetDomain().equals("skill")));
		assertTrue(unresolved.stream().anyMatch(u -> u.ref().equals(minAptSkill)
				&& u.targetDomain().equals("skill")));
		assertTrue(unresolved.stream().anyMatch(u -> u.targetDomain().equals("ability")));
		assertTrue(unresolved.stream().anyMatch(u -> u.targetDomain().equals("resource")));
	}

	// M12-1: icon field — optional, shorthand-normalized, lenient on
	// malformed values (warns into the validation report; file still loads).
	@Test
	void iconFieldLoadsNormalizedAndOptional() {
		Map<ResourceLocation, com.google.gson.JsonElement> files = new LinkedHashMap<>();
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"), JsonParser.parseString(
				"{\"display_name\": \"Mining\", \"category\": \"gathering\", \"icon\": \"skill/mining\"}"));
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging"), JsonParser.parseString(
				"{\"display_name\": \"Foraging\", \"category\": \"gathering\"}"));

		LifepathContent.registerAll("skill", files,
				SkillDefinition.SkillDefinitionFile.CODEC, SkillDefinition::fromFile,
				LifepathContent.skills());

		assertEquals(java.util.Optional.of(
						ResourceLocation.fromNamespaceAndPath("lifepath", "textures/gui/skill/mining.png")),
				LifepathContent.skills().get(ResourceLocation.fromNamespaceAndPath("lifepath", "mining")).icon());
		assertTrue(LifepathContent.skills().get(ResourceLocation.fromNamespaceAndPath("lifepath", "foraging"))
				.icon().isEmpty());
	}

	@Test
	void malformedIconWarnsButFileLoads() {
		Map<ResourceLocation, com.google.gson.JsonElement> files = new LinkedHashMap<>();
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"), JsonParser.parseString(
				"{\"display_name\": \"Mining\", \"category\": \"gathering\", \"icon\": \"bad icon!!\"}"));

		int loaded = LifepathContent.registerAll("skill", files,
				SkillDefinition.SkillDefinitionFile.CODEC, SkillDefinition::fromFile,
				LifepathContent.skills());

		assertEquals(1, loaded);
		assertTrue(LifepathContent.skills().get(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"))
				.icon().isEmpty());

		var report = LifepathContent.validateAll();
		assertTrue(report.issues().stream().anyMatch(i ->
				i.severity() == com.dwurdy.lifepath.registry.ValidationReport.Severity.WARN
						&& i.field().equals("icon")
						&& i.file().equals(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"))));
	}
}
