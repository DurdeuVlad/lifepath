package io.github.durdeuvlad.lifepath.character.migration;

import io.github.durdeuvlad.lifepath.LifepathMod;
import net.minecraft.nbt.NbtCompound;

/**
 * The concrete Lifepath migration chain. Even at {@code DATA_VERSION = 1} the
 * chain is real: the v0→v1 step normalizes pre-versioning blobs (missing
 * {@code data_version}, legacy key shapes) into the v1 layout. New migrations
 * append here — never edit old steps.
 */
public final class CharacterMigrations {
	private static final MigrationChain CHAIN = MigrationChain.builder(LifepathMod.DATA_VERSION)
			.step(new DataMigration() {
				@Override
				public int fromVersion() {
					return 0;
				}

				@Override
				public int toVersion() {
					return 1;
				}

				@Override
				public void migrate(NbtCompound data) {
					// v0 blobs predate versioning; v1 introduced the current
					// field layout, which the codec fills with defaults anyway.
					// This step exists to prove the chain executes end-to-end.
				}
			})
			.build();

	private CharacterMigrations() {
	}

	/** Upgrades {@code data} (mutated in place) to {@link LifepathMod#DATA_VERSION}. */
	public static NbtCompound migrate(NbtCompound data) {
		return CHAIN.migrate(data);
	}
}
