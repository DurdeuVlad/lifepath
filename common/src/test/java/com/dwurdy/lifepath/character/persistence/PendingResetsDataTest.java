package com.dwurdy.lifepath.character.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/**
 * The offline half of {@code resetall}: markers persist as a string UUID
 * list in SavedData, consume one-shot, and tolerate corrupt entries (a
 * malformed marker must not wedge the load — the player just resets anyway).
 * {@code registries} is unused by this codec, so tests pass {@code null}.
 */
class PendingResetsDataTest {

	@Test
	void addMarksPending() {
		PendingResetsData data = new PendingResetsData();
		UUID id = UUID.randomUUID();

		assertFalse(data.isPending(id));
		data.add(id);

		assertTrue(data.isPending(id));
		assertEquals(1, data.size());
		assertTrue(data.isDirty());
	}

	@Test
	void consumeIsOneShot() {
		PendingResetsData data = new PendingResetsData();
		UUID id = UUID.randomUUID();
		data.add(id);

		assertTrue(data.consume(id));
		assertFalse(data.isPending(id));
		assertFalse(data.consume(id));
		assertEquals(0, data.size());
	}

	@Test
	void saveLoadRoundTripsMarkers() {
		PendingResetsData data = new PendingResetsData();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		data.add(a);
		data.add(b);

		PendingResetsData decoded = PendingResetsData.load(
				data.save(new CompoundTag(), null), null);

		assertTrue(decoded.isPending(a));
		assertTrue(decoded.isPending(b));
		assertEquals(2, decoded.size());
	}

	@Test
	void malformedEntriesAreSkippedNotFatal() {
		ListTag list = new ListTag();
		list.add(StringTag.valueOf(UUID.randomUUID().toString()));
		list.add(StringTag.valueOf("not-a-uuid"));
		list.add(StringTag.valueOf("also bad"));
		CompoundTag tag = new CompoundTag();
		tag.put("pending", list);

		PendingResetsData decoded = PendingResetsData.load(tag, null);

		assertEquals(1, decoded.size());
	}

	@Test
	void absentTagLoadsEmpty() {
		PendingResetsData decoded = PendingResetsData.load(new CompoundTag(), null);

		assertEquals(0, decoded.size());
	}
}
