package io.github.durdeuvlad.lifepath.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LifepathContentTest {

	@AfterEach
	void reset() {
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
		LifepathContent.skills().clear();
		LifepathContent.validateReferences();
	}

	@Test
	void registerAllLoadsGoodEntriesAndSkipsMalformed() {
		Map<Identifier, com.google.gson.JsonElement> files = new LinkedHashMap<>();
		files.put(Identifier.of("lifepath", "a"), JsonParser.parseString("{\"display_name\": \"A\"}"));
		files.put(Identifier.of("lifepath", "bad"), JsonParser.parseString("{\"nope\": true}"));
		files.put(Identifier.of("lifepath", "bad_enum"),
				JsonParser.parseString("{\"display_name\": \"X\", \"visibility\": \"bogus\"}"));
		files.put(Identifier.of("lifepath", "b"), JsonParser.parseString("{\"display_name\": \"B\"}"));

		int loaded = LifepathContent.registerAll("species", files,
				SpeciesDefinition.SpeciesDefinitionFile.CODEC, SpeciesDefinition::fromFile,
				LifepathContent.species());

		assertEquals(2, loaded);
		assertTrue(LifepathContent.species().contains(Identifier.of("lifepath", "a")));
		assertTrue(LifepathContent.species().contains(Identifier.of("lifepath", "b")));
		assertFalse(LifepathContent.species().contains(Identifier.of("lifepath", "bad")));
		assertFalse(LifepathContent.species().contains(Identifier.of("lifepath", "bad_enum")));
	}

	@Test
	void entryIdMapsFilePathToContentId() {
		assertEquals(Identifier.of("lifepath", "human"),
				LifepathContent.entryId(Identifier.of("lifepath", "species/human.json"), "species"));
		assertEquals(Identifier.of("mypack", "deep/dir"),
				LifepathContent.entryId(Identifier.of("mypack", "skill/deep/dir.json"), "skill"));
	}

	@Test
	void existsAnswersRegisteredDomainsAndPermitsUnregistered() {
		LifepathContent.skills().register(Identifier.of("lifepath", "foraging"),
				new SkillDefinition(Identifier.of("lifepath", "foraging"), "Foraging",
						SkillDefinition.Category.GATHERING, 100,
						java.util.Optional.empty(), List.of(),
						java.util.Optional.empty(), java.util.Optional.empty()));

		assertTrue(LifepathContent.exists("skill", Identifier.of("lifepath", "foraging")));
		assertFalse(LifepathContent.exists("skill", Identifier.of("lifepath", "gone")));
		assertFalse(LifepathContent.exists("species", Identifier.of("lifepath", "gone")));
		// Domains without a registry yet answer permissively (their milestone hasn't landed).
		assertTrue(LifepathContent.exists("ability", Identifier.of("lifepath", "anything")));
		assertTrue(LifepathContent.exists("trait", Identifier.of("lifepath", "anything")));
	}

	@Test
	void validationRecordsOnlyTrulyUnresolvedRefs() {
		Identifier skillId = Identifier.of("lifepath", "foraging");
		Identifier missingSkill = Identifier.of("lifepath", "missing_skill");
		LifepathContent.skills().register(skillId,
				new SkillDefinition(skillId, "Foraging", SkillDefinition.Category.GATHERING, 100,
						java.util.Optional.empty(), List.of(),
						java.util.Optional.empty(), java.util.Optional.empty()));
		Identifier minAptSkill = Identifier.of("lifepath", "min_apt_skill");
		LifepathContent.species().register(Identifier.of("lifepath", "human"),
				new SpeciesDefinition(Identifier.of("lifepath", "human"), "Human",
						SpeciesDefinition.Visibility.NORMAL, SpeciesDefinition.Selection.OPEN,
						List.of(Identifier.of("lifepath", "fae_grace")), List.of(),
						Map.of(minAptSkill, io.github.durdeuvlad.lifepath.skill.Aptitude.B),
						List.of(Identifier.of("lifepath", "mana")),
						java.util.Optional.empty(), java.util.Optional.empty()));
		LifepathContent.specializations().register(Identifier.of("lifepath", "wanderer"),
				new SpecializationDefinition(Identifier.of("lifepath", "wanderer"), "Wanderer",
						Map.of(skillId, 2, missingSkill, 1), Map.of(), Map.of(), Map.of(), Map.of(),
						List.of()));

		LifepathContent.validateReferences();

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
}
