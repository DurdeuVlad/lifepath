package io.github.durdeuvlad.lifepath.client.character;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.network.s2c.CharacterSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side mirror of the local player's character state, populated solely
 * by {@link CharacterSyncPayload}.
 *
 * <p>Read-only contract: there is no C2S channel for character state, so any
 * mutation of the returned object is local-only garbage — UI code must treat
 * it as a read model. The snapshot may be stale until the next sync and is
 * cleared on disconnect so a reconnect never shows the previous server's data.
 */
@Environment(EnvType.CLIENT)
public final class ClientCharacterState {
	private static volatile PlayerCharacterData snapshot;

	private ClientCharacterState() {
	}

	/** Applies an incoming server snapshot. Called on the client main thread. */
	public static void apply(CharacterSyncPayload payload) {
		snapshot = payload.snapshot();
	}

	/** Drops the snapshot (disconnect / leaving a server). */
	public static void clear() {
		snapshot = null;
	}

	/**
	 * Applies a single-cooldown delta (M4-4 {@code CooldownUpdatePayload}) to
	 * the read-model snapshot so HUD state stays fresh between full syncs.
	 * {@code expiryEpochMs <= 0} clears the entry. No-op before first snapshot.
	 */
	public static void applyCooldown(net.minecraft.util.Identifier abilityId,
			long expiryEpochMs) {
		PlayerCharacterData s = snapshot;
		if (s == null) {
			return;
		}
		if (expiryEpochMs > 0) {
			s.setCooldown(abilityId, expiryEpochMs);
		} else {
			s.removeCooldown(abilityId);
		}
	}

	/** The latest synced snapshot, or null if none has arrived yet. Read-only. */
	@Nullable
	public static PlayerCharacterData snapshot() {
		return snapshot;
	}

	public static boolean hasCharacter() {
		return snapshot != null;
	}
}
