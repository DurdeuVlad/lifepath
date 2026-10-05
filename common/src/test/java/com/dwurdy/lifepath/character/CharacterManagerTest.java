package com.dwurdy.lifepath.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.skill.Aptitude;
import com.dwurdy.lifepath.skill.SkillProgress;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CharacterManagerTest {

	@Test
	void snapshotForSyncPrunesExpiredCooldowns() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		ResourceLocation active = ResourceLocation.fromNamespaceAndPath("lifepath", "active");
		ResourceLocation expired = ResourceLocation.fromNamespaceAndPath("lifepath", "expired");
		data.setCooldown(active, 2000L);
		data.setCooldown(expired, 500L);

		PlayerCharacterData snapshot = CharacterManager.snapshotForSync(data, 1000L);

		assertTrue(snapshot.cooldowns().containsKey(active));
		assertFalse(snapshot.cooldowns().containsKey(expired));
		// The server model keeps its cooldowns — pruning applies to the copy only.
		assertTrue(data.cooldowns().containsKey(expired));
	}

	@Test
	void snapshotForSyncDoesNotShareMutableState() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(ResourceLocation.fromNamespaceAndPath("lifepath", "s"),
				new SkillProgress(5.0, 1, 1, 0, Aptitude.C, 0L));

		PlayerCharacterData snapshot = CharacterManager.snapshotForSync(data, 0L);
		snapshot.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "other"));

		org.junit.jupiter.api.Assertions.assertNull(data.speciesId());
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "other"), snapshot.speciesId());
		assertEquals(data.skills(), snapshot.skills());
	}

	@Test
	void snapshotForSyncDoesNotSeeLaterMutations() {
		// M7-2: a snapshot taken mid-tick must not observe mutations applied
		// to the live model afterwards (the deep-copy guarantee).
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "a"), 5000L);

		PlayerCharacterData snapshot = CharacterManager.snapshotForSync(data, 0L);
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "b"), 5000L);
		data.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "iceborn"));

		assertFalse(snapshot.cooldowns().containsKey(
				ResourceLocation.fromNamespaceAndPath("lifepath", "b")));
		org.junit.jupiter.api.Assertions.assertNull(snapshot.speciesId());
	}

	@Test
	void snapshotForSyncStripsScheduleKeysAndLedger() {
		// M7-2: server bookkeeping never reaches the wire.
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "schedule/mining"), 5000L);
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "real_cd"), 5000L);
		data.setActionTimestamps("sig/mining",
				new java.util.ArrayList<>(java.util.List.of(1L)));

		PlayerCharacterData snapshot = CharacterManager.snapshotForSync(data, 0L);
		assertTrue(snapshot.cooldowns().containsKey(
				ResourceLocation.fromNamespaceAndPath("lifepath", "real_cd")));
		assertFalse(snapshot.cooldowns().keySet().stream().anyMatch(
				com.dwurdy.lifepath.ability.CooldownService::isScheduleKey));
		assertTrue(snapshot.actionSignatures().isEmpty());
	}
}
