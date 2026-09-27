package io.github.durdeuvlad.lifepath.character;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.persistence.CharacterPersistence;
import io.github.durdeuvlad.lifepath.platform.Platform;
import java.nio.file.Path;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Entity-attachment plumbing for {@link PlayerCharacterData}.
 *
 * <p>Storage: a {@code copyOnDeath} attachment of raw {@link CompoundTag} —
 * persists through death/respawn (the documented "nothing resets on death"
 * rule), logout/login, restarts and dimension changes. Raw NBT (not a typed
 * attachment) so {@link CharacterPersistence} controls decode order —
 * migrate → codec → sanitize — and can quarantine corrupt blobs instead of
 * letting an attachment decode failure break player login.
 *
 * <p>Access contract: {@link #get} returns a <b>detached snapshot</b> decoded
 * fresh from storage — mutate it, then call {@link #set} to persist. A blob
 * migrated from an older version is written back once (so it doesn't
 * re-migrate on every read); a corrupt blob is quarantined to a backup file
 * AND overwritten with defaults, so degradation is one-shot — never repeated
 * failures, disk spam, or a broken join.
 */
public final class CharacterAttachments {
	private CharacterAttachments() {
	}

	/**
	 * Registers the {@code lifepath:character_data} attachment. Called once via
	 * {@code RegistryBootstrap}; the platform owns the mechanism (Fabric
	 * attachment, NeoForge attachment — M13-4) and the idempotence.
	 */
	public static void init() {
		Platform.get().registerCharacterAttachment();
	}

	/**
	 * Loads (migrating + sanitizing) the player's character data. A first-join
	 * player gets valid defaults; a corrupt blob becomes backup + repaired
	 * defaults. Never throws for data reasons.
	 */
	public static PlayerCharacterData get(ServerPlayer player) {
		CompoundTag raw = Platform.get().getCharacterData(player);
		if (raw == null || raw.isEmpty()) {
			return PlayerCharacterData.createDefault();
		}
		try {
			PlayerCharacterData data = CharacterPersistence.deserialize(raw);
			if (versionOf(raw) < LifepathMod.DATA_VERSION) {
				set(player, data);
			}
			return data;
		} catch (Exception e) {
			Path backup = CharacterPersistence.writeBackup(raw, player.getUUID(), backupDir(player));
			LifepathMod.LOGGER.error("corrupt character data for {} (backup: {}); repairing with defaults",
					player.getUUID(), backup, e);
			PlayerCharacterData defaults = PlayerCharacterData.createDefault();
			set(player, defaults);
			return defaults;
		}
	}

	/** Serializes and stores the player's character data. */
	public static void set(ServerPlayer player, PlayerCharacterData data) {
		Platform.get().setCharacterData(player, CharacterPersistence.serialize(data));
	}

	private static int versionOf(CompoundTag raw) {
		return raw.contains("data_version") ? raw.getInt("data_version") : 0;
	}

	@Nullable
	private static Path backupDir(ServerPlayer player) {
		var server = player.getCommandSenderWorld().getServer();
		if (server == null) {
			return null;
		}
		return server.getWorldPath(LevelResource.ROOT).resolve("lifepath");
	}
}
