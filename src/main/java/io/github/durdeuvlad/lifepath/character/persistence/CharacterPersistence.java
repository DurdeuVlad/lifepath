package io.github.durdeuvlad.lifepath.character.persistence;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.ContentIndex;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.character.migration.CharacterMigrations;
import io.github.durdeuvlad.lifepath.util.Serialization;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Serialization boundary for {@link PlayerCharacterData}: raw NBT in,
 * validated model out.
 *
 * <p>Persistence mechanism: a {@code copyOnDeath} Fabric entity attachment
 * holding raw {@link NbtCompound} (see {@code CharacterAttachments}). Raw NBT
 * is stored rather than a typed attachment so that (a) migrations can reshape
 * the blob before the codec sees it, and (b) a corrupt blob can be quarantined
 * to a backup file instead of breaking player login.
 *
 * <p>Load order: migrate (raw NBT, v&lt;current steps in order) → codec decode →
 * sanitize against {@link ContentIndex} (unknown content IDs dropped with WARN).
 * Any failure after migration → the raw blob is written to
 * {@code <backupDir>/corrupt/<uuid>-<millis>.snbt}, ERROR logged, and fresh
 * defaults returned. A character load never crashes a join.
 */
public final class CharacterPersistence {
	private static volatile ContentIndex contentIndex = ContentIndex.PERMISSIVE;

	private CharacterPersistence() {
	}

	/** Installs the existence view used by sanitization. M1-3 wires real registries here. */
	public static void setContentIndex(ContentIndex index) {
		contentIndex = index;
	}

	public static NbtCompound serialize(PlayerCharacterData data) {
		return (NbtCompound) Serialization.toNbt(PlayerCharacterData.CODEC, data);
	}

	/** Migrates + decodes + sanitizes. Throws on decode/migration failure. */
	public static PlayerCharacterData deserialize(NbtCompound raw) {
		NbtCompound migrated = CharacterMigrations.migrate(raw.copy());
		PlayerCharacterData data = Serialization.fromNbt(PlayerCharacterData.CODEC, migrated);
		data.setDataVersion(LifepathMod.DATA_VERSION);
		return sanitize(data);
	}

	/** {@link #deserialize} with graceful degradation: backup + fresh defaults + ERROR on failure. */
	public static PlayerCharacterData loadSafe(NbtCompound raw, UUID owner, @Nullable Path backupDir) {
		try {
			return deserialize(raw);
		} catch (Exception e) {
			Path backup = writeBackup(raw, owner, backupDir);
			LifepathMod.LOGGER.error("corrupt character data for {} (backup: {}); loading fresh defaults",
					owner, backup, e);
			return PlayerCharacterData.createDefault();
		}
	}

	/** Drops every content reference the index says is gone, WARNing per entry. */
	public static PlayerCharacterData sanitize(PlayerCharacterData data) {
		if (unknown("species", data.speciesId())) {
			drop("species", data.speciesId());
			data.setSpeciesId(null);
		}
		if (unknown("specialization", data.specializationId())) {
			drop("specialization", data.specializationId());
			data.setSpecializationId(null);
		}
		for (Identifier id : new java.util.ArrayList<>(data.skills().keySet())) {
			if (unknown("skill", id)) {
				drop("skill", id);
				data.removeSkill(id);
			}
		}
		for (Identifier id : new java.util.ArrayList<>(data.resources().keySet())) {
			if (unknown("resource", id)) {
				drop("resource", id);
				data.removeResource(id);
			}
		}
		for (Identifier id : new java.util.ArrayList<>(data.cooldowns().keySet())) {
			if (unknown("ability", id)) {
				drop("ability", id);
				data.removeCooldown(id);
			}
		}
		for (PlayerCharacterData.ListKind list : PlayerCharacterData.ListKind.values()) {
			String domain = switch (list) {
				case TRAITS -> "trait";
				case CONDITIONS -> "condition";
				case ATTUNEMENTS -> "attunement";
				case UNLOCKS -> "ability";
			};
			for (Identifier id : new java.util.ArrayList<>(data.list(list))) {
				if (unknown(domain, id)) {
					drop(domain, id);
					data.removeId(list, id);
				}
			}
		}
		return data;
	}

	private static boolean unknown(String domain, @Nullable Identifier id) {
		return id != null && !contentIndex.exists(domain, id);
	}

	private static void drop(String domain, Identifier id) {
		LifepathMod.LOGGER.warn("dropping reference to missing {} definition {}", domain, id);
	}

	private static Path writeBackup(NbtCompound raw, UUID owner, @Nullable Path backupDir) {
		if (backupDir == null) {
			return null;
		}
		try {
			Path dir = backupDir.resolve("corrupt");
			Files.createDirectories(dir);
			Path file = dir.resolve(owner + "-" + System.currentTimeMillis() + ".snbt");
			Files.writeString(file, raw.toString());
			return file;
		} catch (IOException e) {
			LifepathMod.LOGGER.error("failed to write corrupt-data backup for {}", owner, e);
			return null;
		}
	}
}
