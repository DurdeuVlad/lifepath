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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
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
		CompoundTag corrupt = new CompoundTag();
		corrupt.putString("skills", "not_a_map"); // wrong shape -> decode fails

		PlayerCharacterData data = CharacterPersistence.loadSafe(
				corrupt, UUID.randomUUID(), tempDir);

		assertEquals(PlayerCharacterData.createDefault(), data);
		assertTrue(backupFiles() > 0, "expected a .snbt backup to be written");
	}

	@Test
	void scheduleMarkersSurviveSanitize() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		ResourceLocation marker = ResourceLocation.fromNamespaceAndPath("lifepath", "schedule/lifepath/passive_x");
		ResourceLocation real = ResourceLocation.fromNamespaceAndPath("lifepath", "real_ability");
		data.setCooldown(marker, 123L);
		data.setCooldown(real, 456L);
		CharacterPersistence.setContentIndex((domain, id) -> false); // strictest: all unknown
		try {
			PlayerCharacterData sanitized = CharacterPersistence.deserialize(
					CharacterPersistence.serialize(data));
			// Engine bookkeeping survives; the unknown ability cooldown drops.
			assertTrue(sanitized.cooldowns().containsKey(marker));
			assertTrue(!sanitized.cooldowns().containsKey(real));
		} finally {
			CharacterPersistence.setContentIndex(ContentIndex.PERMISSIVE);
		}
	}

	@Test
	void unknownContentIdsAreDroppedOnLoad() {
		PlayerCharacterData data = sampleData();
		CharacterPersistence.setContentIndex(new ContentIndex() {
			@Override
			public boolean exists(String domain, ResourceLocation id) {
				// Everything is gone except the skill and the species.
				return "skill".equals(domain) || "species".equals(domain);
			}
		});
		try {
			PlayerCharacterData sanitized = CharacterPersistence.deserialize(
					CharacterPersistence.serialize(data));

			assertTrue(sanitized.skills().containsKey(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill")));
			assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "test_species"), sanitized.speciesId());
			assertNull(sanitized.specializationId());
			assertTrue(sanitized.traits().isEmpty());
			assertTrue(sanitized.resources().isEmpty());
			assertTrue(sanitized.cooldowns().isEmpty());
			assertTrue(sanitized.unlocks().isEmpty());
		} finally {
			CharacterPersistence.setContentIndex(ContentIndex.PERMISSIVE);
		}
	}

	/**
	 * M-1 (morph): a morph state naming a deleted morph_form clears entirely
	 * on load — the form can't render or stat, so keeping it would corrupt.
	 */
	@Test
	void unknownMorphFormDropsOnLoad() {
		PlayerCharacterData data = sampleData();
		data.setMorph(new PlayerCharacterData.MorphState(
				ResourceLocation.fromNamespaceAndPath("lifepath", "fox"), true, 42L));
		CharacterPersistence.setContentIndex(new ContentIndex() {
			@Override
			public boolean exists(String domain, ResourceLocation id) {
				// Everything resolves except the morph_form domain.
				return !"morph_form".equals(domain);
			}
		});
		try {
			PlayerCharacterData sanitized = CharacterPersistence.deserialize(
					CharacterPersistence.serialize(data));
			assertNull(sanitized.morph(),
					"morph on a deleted form must drop with the rest of the state");
		} finally {
			CharacterPersistence.setContentIndex(ContentIndex.PERMISSIVE);
		}
	}

	/**
	 * M9-4: unlocks[] holds gated-content ids (species), so a granted unlock
	 * must survive relog while the gated content still exists — the
	 * {@code unlock_content} domain, not the ability registry, is its home.
	 */
	@Test
	void heldUnlockSurvivesRelogWhenGatedContentExists() {
		PlayerCharacterData data = sampleData();
		CharacterPersistence.setContentIndex(new ContentIndex() {
			@Override
			public boolean exists(String domain, ResourceLocation id) {
				// Everything gone except skill/species — and unlock_content,
				// which the real index resolves via species or granting defs.
				return "skill".equals(domain) || "species".equals(domain)
						|| "unlock_content".equals(domain);
			}
		});
		try {
			PlayerCharacterData sanitized = CharacterPersistence.deserialize(
					CharacterPersistence.serialize(data));
			assertTrue(sanitized.unlocks()
					.contains(ResourceLocation.fromNamespaceAndPath("lifepath", "u1")),
					"a held unlock id must survive relog while its content exists");
		} finally {
			CharacterPersistence.setContentIndex(ContentIndex.PERMISSIVE);
		}
	}

	/**
	 * M10-2 end-to-end fixture: a hand-built {@code data_version: 1} blob —
	 * conditions stored as a bare id <i>list</i> (the pre-v2 shape) — runs the
	 * real {@code migrate -> decode -> sanitize} path and yields valid v2 data.
	 */
	@Test
	void v1FixtureMigratesThroughTheRealPath() {
		CompoundTag v1 = new CompoundTag();
		v1.putInt("data_version", 1);
		v1.putString("species_id", "lifepath:human");
		ListTag oldConditions = new ListTag();
		oldConditions.add(StringTag.valueOf("lifepath:vampirism"));
		oldConditions.add(StringTag.valueOf("lifepath:lycanthropy"));
		v1.put("conditions", oldConditions);
		ListTag unlocks = new ListTag();
		unlocks.add(StringTag.valueOf("lifepath:phantom"));
		v1.put("unlocks", unlocks);
		v1.put("skills", new CompoundTag());
		v1.put("cooldowns", new CompoundTag());
		v1.put("resources", new CompoundTag());

		PlayerCharacterData data = CharacterPersistence.deserialize(v1);

		assertEquals(LifepathMod.DATA_VERSION, data.dataVersion(),
				"migrated blob must stamp the current data version");
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "human"), data.speciesId());
		assertTrue(data.conditionState(ResourceLocation.fromNamespaceAndPath("lifepath", "vampirism")) != null,
				"v1 bare condition ids must become v2 condition state entries");
		assertEquals(0, data.conditionState(
				ResourceLocation.fromNamespaceAndPath("lifepath", "vampirism")).stage(),
				"migrated conditions land at stage 0");
		assertTrue(data.unlocks().contains(ResourceLocation.fromNamespaceAndPath("lifepath", "phantom")),
				"unlocks were already a bare id list in v1 and survive untouched");
	}

	/**
	 * M10-2: removed/renamed content degrades to the documented fallback —
	 * unknown ids drop on load; the save stays valid.
	 */
	@Test
	void removedContentIdDropsWithoutCorrupting() {
		CompoundTag v2 = CharacterPersistence.serialize(sampleData());
		// Simulate a datapack that deleted a species while a save references
		// it — every other domain still resolves.
		CharacterPersistence.setContentIndex(new ContentIndex() {
			@Override
			public boolean exists(String domain, ResourceLocation id) {
				return !"species".equals(domain);
			}
		});
		try {
			PlayerCharacterData sanitized = CharacterPersistence.deserialize(v2);
			assertNull(sanitized.speciesId(),
					"removed species id -> null, not crash or dangling ref");
			// The rest of the blob is untouched — drop is surgical.
			assertTrue(sanitized.skills()
					.containsKey(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill")),
					"unrelated known content survives the same load");
			assertEquals(LifepathMod.DATA_VERSION, sanitized.dataVersion());
		} finally {
			CharacterPersistence.setContentIndex(ContentIndex.PERMISSIVE);
		}
	}

	@Test
	void knownSkillWithBrokenInvariantsIsRepairedOnLoad() {
		ResourceLocation skill = ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill");
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
			assertEquals(60, p.protectedFloor()); // floor clamps to [0, maxLevel=60] — may exceed level
			assertEquals(0.0, p.xp());
			assertEquals(0, p.lastMeaningfulUse());
		} finally {
			LifepathContent.skills().clear();
		}
	}

	@Test
	void v1ConditionListMigratesToV2StateMap() {
		// v1 stored conditions as a list of bare id strings; v2 stores
		// {id: {stage,…}}. The migration must preserve every held id at
		// stage 0 and drop nothing.
		CompoundTag raw = CharacterPersistence.serialize(sampleData());
		raw.putInt("data_version", 1);
		net.minecraft.nbt.ListTag legacy = new net.minecraft.nbt.ListTag();
		legacy.add(net.minecraft.nbt.StringTag.valueOf("lifepath:vampirism"));
		legacy.add(net.minecraft.nbt.StringTag.valueOf("lifepath:lycanthropy"));
		raw.put("conditions", legacy);

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertEquals(2, decoded.conditionStates().size());
		assertEquals(0, decoded.conditionState(ResourceLocation.fromNamespaceAndPath("lifepath", "vampirism")).stage());
		assertEquals(0, decoded.conditionState(ResourceLocation.fromNamespaceAndPath("lifepath", "lycanthropy")).stage());
	}

	@Test
	void emptyV1ConditionListMigratesCleanly() {
		CompoundTag raw = CharacterPersistence.serialize(sampleData());
		raw.putInt("data_version", 1);
		raw.put("conditions", new net.minecraft.nbt.ListTag());

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertTrue(decoded.conditionStates().isEmpty());
		assertEquals(LifepathMod.DATA_VERSION, decoded.dataVersion());
	}

	@Test
	void migrationRunsBeforeDecode() {
		// A v0 blob: no data_version, otherwise valid v1 shape.
		CompoundTag raw = CharacterPersistence.serialize(sampleData());
		raw.remove("data_version");

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertEquals(LifepathMod.DATA_VERSION, decoded.dataVersion());
	}

	@Test
	void newerVersionBlobKeepsItsVersion() {
		// Forward-compat: a blob from a NEWER mod version must not be restamped
		// to the current version (that would defeat the migration chain's
		// "leave what we don't understand" contract and invite double-migrations).
		CompoundTag raw = CharacterPersistence.serialize(sampleData());
		raw.putInt("data_version", LifepathMod.DATA_VERSION + 4);

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertEquals(LifepathMod.DATA_VERSION + 4, decoded.dataVersion());
	}

	@Test
	void unknownAptitudeDefaultsToBWithoutNukingBlob() {
		CompoundTag raw = CharacterPersistence.serialize(sampleData());
		CompoundTag skill = raw.getCompound("skills").getCompound("lifepath:test_skill");
		skill.putString("aptitude", "zzz");

		PlayerCharacterData decoded = CharacterPersistence.deserialize(raw);

		assertEquals(Aptitude.B, decoded.skills()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill")).aptitude());
		assertEquals(10.0, decoded.skills()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill")).xp());
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
		data.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_species"));
		data.setSpecializationId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_spec"));
		data.setSkillProgress(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill"),
				new SkillProgress(10.0, 2, 3, 1, Aptitude.A, 42L));
		data.addId(ListKind.TRAITS, ResourceLocation.fromNamespaceAndPath("lifepath", "t1"));
		data.addId(ListKind.UNLOCKS, ResourceLocation.fromNamespaceAndPath("lifepath", "u1"));
		data.setResource(ResourceLocation.fromNamespaceAndPath("lifepath", "mana"), new ResourceState(5.0, 0.0, 10.0));
		// Live cooldown (M4-4): entries with <= persist_min_seconds remaining at
		// load are dropped by sanitize, so the round-trip fixture must use a
		// genuinely live expiry.
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "ab1"),
				System.currentTimeMillis() + 60_000L);
		data.setDataVersion(LifepathMod.DATA_VERSION);
		return data;
	}
}
