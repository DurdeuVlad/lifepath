package com.dwurdy.lifepath.client.character;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.network.s2c.CharacterSyncPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientCharacterStateTest {

	@AfterEach
	void reset() {
		ClientCharacterState.clear();
	}

	@Test
	void applyPopulatesSnapshot() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();

		ClientCharacterState.apply(new CharacterSyncPayload(data));

		assertTrue(ClientCharacterState.hasCharacter());
		assertSame(data, ClientCharacterState.snapshot());
	}

	@Test
	void clearDropsSnapshotSoReconnectShowsNothingStale() {
		ClientCharacterState.apply(new CharacterSyncPayload(PlayerCharacterData.createDefault()));

		ClientCharacterState.clear();

		assertFalse(ClientCharacterState.hasCharacter());
		assertNull(ClientCharacterState.snapshot());
	}
}
