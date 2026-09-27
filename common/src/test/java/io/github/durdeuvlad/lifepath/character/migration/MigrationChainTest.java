package io.github.durdeuvlad.lifepath.character.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MigrationChainTest {

	@Test
	void missingVersionMigratesFromZero() {
		List<Integer> ran = new ArrayList<>();
		MigrationChain chain = MigrationChain.builder(2)
				.step(step(0, ran))
				.step(step(1, ran))
				.build();

		chain.migrate(new CompoundTag());

		assertEquals(List.of(0, 1), ran);
	}

	@Test
	void concreteChainStampsDataVersion() {
		CompoundTag raw = new CompoundTag();

		CompoundTag migrated = CharacterMigrations.migrate(raw);

		assertEquals(io.github.durdeuvlad.lifepath.LifepathMod.DATA_VERSION, migrated.getInt("data_version"));
	}

	@Test
	void newerVersionDataIsLeftUntouched() {
		CompoundTag raw = new CompoundTag();
		raw.putInt("data_version", 99);
		raw.putString("future_field", "keepme");

		CompoundTag migrated = CharacterMigrations.migrate(raw);

		assertEquals(99, migrated.getInt("data_version"));
		assertEquals("keepme", migrated.getString("future_field"));
	}

	@Test
	void chainGapThrows() {
		MigrationChain chain = MigrationChain.builder(2)
				.step(step(1, new ArrayList<>()))
				.build();

		assertThrows(IllegalStateException.class, () -> chain.migrate(new CompoundTag()));
	}

	@Test
	void multiVersionJumpIsNotAllowed() {
		MigrationChain.Builder builder = MigrationChain.builder(3);
		DataMigration jump = new DataMigration() {
			@Override
			public int fromVersion() {
				return 0;
			}

			@Override
			public int toVersion() {
				return 3;
			}

			@Override
			public void migrate(CompoundTag data) {
			}
		};

		assertThrows(IllegalArgumentException.class, () -> builder.step(jump));
	}

	private static DataMigration step(int from, List<Integer> ran) {
		return new DataMigration() {
			@Override
			public int fromVersion() {
				return from;
			}

			@Override
			public int toVersion() {
				return from + 1;
			}

			@Override
			public void migrate(CompoundTag data) {
				ran.add(from);
			}
		};
	}
}
