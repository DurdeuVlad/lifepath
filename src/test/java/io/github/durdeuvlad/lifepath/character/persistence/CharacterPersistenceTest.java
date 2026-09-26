package io.github.durdeuvlad.lifepath.character.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.ContentIndex;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.ListKind;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.ResourceState;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharacterPersistenceTest {

	@TempDir
	Path tempDir;

	@Test
	void serializeDeserializeRoundTrips() {
		PlayerCharacterData data = sampleData();

		PlayerCharacterData decoded = CharacterPersistence.deserialize(
				CharacterPersistence.serialize(data));

		assertEquals(data, decoded);
		assertEquals(LifepathMod.DATA_VERSION, decoded.dataVersion());
	}

	@Test
	void corruptBlobYieldsDefaultsPlusBackup() {
		NbtCompound corrupt = new NbtCompound();
		corrupt.putString("skills", "not_a_map"); // wrong shape -> decode fails

		PlayerCharacterData data = CharacterPersistence.loadSafe(
				corrupt, UUID.randomUUID(), tempDir);

		assertEquals(PlayerCharacterData.createDefault(), data);
		assertTrue(backupFiles() > 0, "expected a .snbt backup to be written");
	}

	@Test
	void unknownContentIdsAreDroppedOnLoad() {
		PlayerCharacterData data = sampleData();
		CharacterPersistence.setContentIndex(new ContentIndex() {
			@Override
			public boolean exists(String domain, Identifier id) {
				// Everything is gone except the skill and the species.
				return "skill".equals(domain) || "species".equals(domain);
			}
		});
		try {
			PlayerCharacterData sanitized = CharacterPersistence.deserialize(
					CharacterPersistence.serialize(data));

			assertTrue(sanitized.skills().containsKey(Identifier.of("lifepath", "test_skill")));
			assertEquals(Identifier.of("lifepath", "test_species"), sanitized.speciesId());
			assertNull(sanitized.specializationId());
			assertTrue(sanitized.traits().isEmpty());
			assertTrue(sanitized.resources().isEmpty());
			assertTrue(sanitized.cooldowns().isEmpty());
			assertTrue(sanitized.unlocks().isEmpty());
		} finally {
			CharacterPersistence.setContentIndex(ContentIndex.PERMISSIVE);
		}
	}

	@Test
	void knownSkillWithBrokenInvariantsIsRepairedOnLoad() {
		Identifier skill = Identifier.of("lifepath", "test_skill");
		SkillDefinition.SkillDefinitionFile file = SkillDefinition.SkillDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(
						"{\"display_name\": \"T\", \"category\": \"gathering\", \"max_level\": 60}"))
				.result().orElseThrow();
		LifepathContent.skills().register(skill, SkillDefinition.fromFile(skill, file));
		try {
			PlayerCharacterData data = sampleData();
			// Corrupt the record: level above def max, floor above level, NaN xp.
			data.setSkillProgress(skill,
					new SkillProgress(Double.NaN, 400, 400, 999, Aptitude.B, -7));
			PlayerCharacterData decoded = CharacterPersistence.deserialize(
					CharacterPersistence.serialize(data));
			SkillProgress p = decoded.skill(skill);
			assertEquals(60, p.level());
			assertEquals(400, p.highestLevel()); // historical peak never lowered
			assertEquals(60, p.protectedFloor());
			assertEquals(0.0, p.xp());
			assertEquals(0, p.lastMeaningfulUse());
		} finally {
			LifepathContent.skills().clear();
		}
	}

	@Test
	void migrationRunsBeforeDecode() {
		// A v0 blob: no data_version, otherwise valid v1 shape.
		NbtCompound raw = CharacterPersistence.serialize(sampleData());
		raw.remove("data_version");

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertEquals(LifepathMod.DATA_VERSION, decoded.dataVersion());
	}

	@Test
	void newerVersionBlobKeepsItsVersion() {
		// Forward-compat: a blob from a NEWER mod version must not be restamped
		// to the current version (that would defeat the migration chain's
		// "leave what we don't understand" contract and invite double-migrations).
		NbtCompound raw = CharacterPersistence.serialize(sampleData());
		raw.putInt("data_version", LifepathMod.DATA_VERSION + 4);

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertEquals(LifepathMod.DATA_VERSION + 4, decoded.dataVersion());
	}

	@Test
	void unknownAptitudeDefaultsToBWithoutNukingBlob() {
		NbtCompound raw = CharacterPersistence.serialize(sampleData());
		NbtCompound skill = raw.getCompound("skills").getCompound("lifepath:test_skill");
		skill.putString("aptitude", "zzz");

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertEquals(Aptitude.B, decoded.skills()
				.get(Identifier.of("lifepath", "test_skill")).aptitude());
		assertEquals(10.0, decoded.skills()
				.get(Identifier.of("lifepath", "test_skill")).xp());
	}

	private long backupFiles() {
		try (var files = Files.list(tempDir.resolve("corrupt"))) {
			return files.count();
		} catch (java.io.IOException e) {
			return 0;
		}
	}

	private static PlayerCharacterData sampleData() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(Identifier.of("lifepath", "test_species"));
		data.setSpecializationId(Identifier.of("lifepath", "test_spec"));
		data.setSkillProgress(Identifier.of("lifepath", "test_skill"),
				new SkillProgress(10.0, 2, 3, 1, Aptitude.A, 42L));
		data.addId(ListKind.TRAITS, Identifier.of("lifepath", "t1"));
		data.addId(ListKind.UNLOCKS, Identifier.of("lifepath", "u1"));
		data.setResource(Identifier.of("lifepath", "mana"), new ResourceState(5.0, 0.0, 10.0));
		data.setCooldown(Identifier.of("lifepath", "ab1"), 100L);
		data.setDataVersion(LifepathMod.DATA_VERSION);
		return data;
	}
}
