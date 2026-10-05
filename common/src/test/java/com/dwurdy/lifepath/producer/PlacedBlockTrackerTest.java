package com.dwurdy.lifepath.producer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlacedBlockTrackerTest {
	private static final ResourceKey<Level> OVERWORLD =
			ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
	private static final ResourceKey<Level> NETHER =
			ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"));

	@BeforeEach
	void setUp() {
		PlacedBlockTracker.resetForTests();
	}

	@Test
	void recordThenConsumeIsOneShot() {
		BlockPos pos = new BlockPos(1, 64, 1);
		PlacedBlockTracker.record(OVERWORLD, pos);
		assertTrue(PlacedBlockTracker.contains(OVERWORLD, pos));
		assertTrue(PlacedBlockTracker.consume(OVERWORLD, pos));
		assertFalse(PlacedBlockTracker.contains(OVERWORLD, pos));
		// Second break at the same position finds nothing (one placement = one suppression).
		assertFalse(PlacedBlockTracker.consume(OVERWORLD, pos));
	}

	@Test
	void worldsAreIsolated() {
		BlockPos pos = new BlockPos(5, 64, 5);
		PlacedBlockTracker.record(OVERWORLD, pos);
		assertFalse(PlacedBlockTracker.contains(NETHER, pos));
		assertTrue(PlacedBlockTracker.consume(OVERWORLD, pos));
	}

	@Test
	void capacityEvictsOldest() {
		for (int i = 0; i < PlacedBlockTracker.CAPACITY_PER_WORLD + 10; i++) {
			PlacedBlockTracker.record(OVERWORLD, new BlockPos(i, 0, 0));
		}
		// Oldest evicted; newest retained.
		assertFalse(PlacedBlockTracker.contains(OVERWORLD, new BlockPos(0, 0, 0)));
		assertTrue(PlacedBlockTracker.contains(OVERWORLD,
				new BlockPos(PlacedBlockTracker.CAPACITY_PER_WORLD + 9, 0, 0)));
	}

	@Test
	void mutablePosDoesNotCorruptRecord() {
		BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(2, 3, 4);
		PlacedBlockTracker.record(OVERWORLD, mutable);
		mutable.set(99, 99, 99); // mutate AFTER record
		assertTrue(PlacedBlockTracker.contains(OVERWORLD, new BlockPos(2, 3, 4)));
		assertFalse(PlacedBlockTracker.contains(OVERWORLD, new BlockPos(99, 99, 99)));
	}
}
