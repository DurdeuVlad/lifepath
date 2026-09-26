package io.github.durdeuvlad.lifepath.character;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.persistence.CharacterPersistence;
import java.nio.file.Path;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

/**
 * Entity-attachment plumbing for {@link PlayerCharacterData}.
 *
 * <p>Storage: a {@code copyOnDeath} attachment of raw {@link NbtCompound} —
 * persists through death/respawn (the documented "nothing resets on death"
 * rule), logout/login, restarts and dimension changes. Raw NBT (not a typed
 * attachment) so {@link CharacterPersistence} controls decode order —
 * migrate → codec → sanitize — and can quarantine corrupt blobs instead of
 * letting an attachment decode failure break player login.
 */
public final class CharacterAttachments {
	private static AttachmentType<NbtCompound> characterData;

	private CharacterAttachments() {
	}

	/** Registers the attachment type. Called once via {@code RegistryBootstrap}. */
	public static void init() {
		characterData = AttachmentRegistry.<NbtCompound>builder()
				.persistent(NbtCompound.CODEC)
				.copyOnDeath()
				.initializer(NbtCompound::new)
				.buildAndRegister(LifepathMod.id("character_data"));
	}

	/**
	 * Loads (migrating + sanitizing) the player's character data. A first-join
	 * player gets valid defaults; a corrupt blob becomes backup + defaults.
	 */
	public static PlayerCharacterData get(ServerPlayerEntity player) {
		NbtCompound raw = player.getAttached(characterData);
		if (raw == null) {
			raw = new NbtCompound();
		}
		return CharacterPersistence.loadSafe(raw, player.getUuid(), backupDir(player));
	}

	/** Serializes and stores the player's character data. */
	public static void set(ServerPlayerEntity player, PlayerCharacterData data) {
		player.setAttached(characterData, CharacterPersistence.serialize(data));
	}

	@Nullable
	private static Path backupDir(ServerPlayerEntity player) {
		var server = player.getServer();
		if (server == null) {
			return null;
		}
		return server.getSavePath(WorldSavePath.ROOT).resolve("lifepath");
	}
}
