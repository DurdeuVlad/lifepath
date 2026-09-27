package io.github.durdeuvlad.lifepath.character.migration;

import io.github.durdeuvlad.lifepath.LifepathMod;
import net.minecraft.nbt.CompoundTag;

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
				public void migrate(CompoundTag data) {
					// v0 blobs predate versioning; v1 introduced the current
					// field layout, which the codec fills with defaults anyway.
					// This step exists to prove the chain executes end-to-end.
				}
			})
			.step(new DataMigration() {
				@Override
				public int fromVersion() {
					return 1;
				}

				@Override
				public int toVersion() {
					return 2;
				}

				@Override
				public void migrate(CompoundTag data) {
					// v1 stored `conditions` as a list of bare id strings
					// (dead field — nothing ever granted them, but upgrade
					// anyway). v2 stores a compound {id: {stage,…}}.
					if (!(data.get("conditions") instanceof net.minecraft.nbt.ListTag old)
							|| old.isEmpty()) {
						data.put("conditions", new net.minecraft.nbt.CompoundTag());
						return;
					}
					var map = new net.minecraft.nbt.CompoundTag();
					for (int i = 0; i < old.size(); i++) {
						String id = old.getString(i);
						if (!id.isEmpty()) {
							map.put(id, new net.minecraft.nbt.CompoundTag());
						}
					}
					data.put("conditions", map);
				}
			})
			.build();

	private CharacterMigrations() {
	}

	/** Upgrades {@code data} (mutated in place) to {@link LifepathMod#DATA_VERSION}. */
	public static CompoundTag migrate(CompoundTag data) {
		return CHAIN.migrate(data);
	}
}
