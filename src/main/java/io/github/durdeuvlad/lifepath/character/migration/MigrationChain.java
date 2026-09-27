package io.github.durdeuvlad.lifepath.character.migration;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

/**
 * Ordered chain of {@link DataMigration}s. {@link #migrate(CompoundTag)} reads
 * the blob's {@code data_version} and applies every step needed to reach
 * {@link #targetVersion()}. A missing {@code data_version} is treated as 0
 * (pre-versioning data). Data written by a NEWER mod version is left untouched
 * with a WARN (forward-compatibility: never mangle what we don't understand).
 *
 * <p>Chain gaps are a programming error: reaching a version with no matching
 * step throws {@link IllegalStateException}, which the persistence layer turns
 * into backup + defaults + ERROR (graceful degradation).
 */
public final class MigrationChain {
	private final int targetVersion;
	private final List<DataMigration> migrations;

	private MigrationChain(int targetVersion, List<DataMigration> migrations) {
		this.targetVersion = targetVersion;
		this.migrations = List.copyOf(migrations);
	}

	public static Builder builder(int targetVersion) {
		return new Builder(targetVersion);
	}

	public int targetVersion() {
		return targetVersion;
	}

	public CompoundTag migrate(CompoundTag data) {
		int version = data.contains("data_version") ? data.getInt("data_version") : 0;
		if (version > targetVersion) {
			LifepathMod.LOGGER.warn("character data version {} is newer than mod's {}; loading as-is",
					version, targetVersion);
			return data;
		}
		while (version < targetVersion) {
			DataMigration step = findFrom(version);
			if (step == null) {
				throw new IllegalStateException(
						"migration chain gap: no step from data version " + version);
			}
			step.migrate(data);
			version = step.toVersion();
		}
		data.putInt("data_version", version);
		return data;
	}

	private DataMigration findFrom(int fromVersion) {
		for (DataMigration migration : migrations) {
			if (migration.fromVersion() == fromVersion) {
				return migration;
			}
		}
		return null;
	}

	public static final class Builder {
		private final int targetVersion;
		private final List<DataMigration> migrations = new ArrayList<>();

		private Builder(int targetVersion) {
			this.targetVersion = targetVersion;
		}

		public Builder step(DataMigration migration) {
			if (migration.toVersion() != migration.fromVersion() + 1) {
				throw new IllegalArgumentException("migrations must bump exactly one version");
			}
			for (DataMigration existing : migrations) {
				if (existing.fromVersion() == migration.fromVersion()) {
					throw new IllegalArgumentException(
							"duplicate migration step from version " + migration.fromVersion());
				}
			}
			migrations.add(migration);
			return this;
		}

		public MigrationChain build() {
			migrations.sort(Comparator.comparingInt(DataMigration::fromVersion));
			return new MigrationChain(targetVersion, migrations);
		}
	}
}
