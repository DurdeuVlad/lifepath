package io.github.durdeuvlad.lifepath.character.migration;

import net.minecraft.nbt.CompoundTag;

/**
 * One step in the character-data migration chain: upgrades a serialized
 * {@code PlayerCharacterData} blob from {@link #fromVersion()} to
 * {@link #toVersion()} in place. Migrations operate on the raw NBT BEFORE the
 * codec runs, so they can reshape renamed/removed fields freely.
 */
public interface DataMigration {
	int fromVersion();

	int toVersion();

	void migrate(CompoundTag data);
}
