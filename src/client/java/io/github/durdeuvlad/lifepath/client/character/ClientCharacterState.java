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
	/** Server-resolved display strings (M6-1) — ids stay in the snapshot. */
	private static volatile io.github.durdeuvlad.lifepath.network.s2c
			.IdentitySummaryPayload identity = io.github.durdeuvlad.lifepath
			.network.s2c.IdentitySummaryPayload.empty();
	/** Resource → current band index per the last delta (-1 removed). Server defs aren't client-visible. */
	private static final java.util.Map<net.minecraft.util.Identifier, Integer>
			RESOURCE_BANDS = new java.util.concurrent.ConcurrentHashMap<>();

	private ClientCharacterState() {
	}

	/** Applies an incoming server snapshot. Called on the client main thread. */
	public static void apply(CharacterSyncPayload payload) {
		snapshot = payload.snapshot();
	}

	/** Drops the snapshot (disconnect / leaving a server). */
	public static void clear() {
		snapshot = null;
		identity = io.github.durdeuvlad.lifepath.network.s2c
				.IdentitySummaryPayload.empty();
		RESOURCE_BANDS.clear();
	}

	/** Applies the server's resolved display strings (M6-1). */
	public static void applyIdentity(io.github.durdeuvlad.lifepath.network.s2c
			.IdentitySummaryPayload payload) {
		identity = payload;
	}

	/** Latest identity summary — never null (empty until first sync). */
	public static io.github.durdeuvlad.lifepath.network.s2c
			.IdentitySummaryPayload identity() {
		return identity;
	}

	/**
	 * Applies a resource delta (M4-5 {@code ResourceUpdatePayload}) to the
	 * read-model snapshot + records the band index for M6 feedback.
	 * No-op before the first snapshot.
	 */
	public static void applyResource(net.minecraft.util.Identifier resourceId,
			double current, double min, double max, int bandIndex) {
		PlayerCharacterData s = snapshot;
		if (s == null) {
			return;
		}
		s.setResource(resourceId,
				new PlayerCharacterData.ResourceState(current, min, max));
		if (bandIndex >= 0) {
			RESOURCE_BANDS.put(resourceId, bandIndex);
		} else {
			RESOURCE_BANDS.remove(resourceId);
		}
	}

	/** The band index a resource currently sits in (−1 = none), per the last delta. */
	public static int resourceBand(net.minecraft.util.Identifier resourceId) {
		return RESOURCE_BANDS.getOrDefault(resourceId, -1);
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
