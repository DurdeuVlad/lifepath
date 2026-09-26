package io.github.durdeuvlad.lifepath.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.Aptitude;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.SkillProgress;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class CharacterManagerTest {

	@Test
	void snapshotForSyncPrunesExpiredCooldowns() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		Identifier active = Identifier.of("lifepath", "active");
		Identifier expired = Identifier.of("lifepath", "expired");
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
		data.setSkillProgress(Identifier.of("lifepath", "s"),
				new SkillProgress(5.0, 1, 1, 0, Aptitude.C, 0L));

		PlayerCharacterData snapshot = CharacterManager.snapshotForSync(data, 0L);
		snapshot.setSpeciesId(Identifier.of("lifepath", "other"));

		org.junit.jupiter.api.Assertions.assertNull(data.speciesId());
		assertEquals(Identifier.of("lifepath", "other"), snapshot.speciesId());
		assertEquals(data.skills(), snapshot.skills());
	}
}
