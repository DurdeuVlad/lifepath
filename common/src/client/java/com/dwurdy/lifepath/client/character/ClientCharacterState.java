package com.dwurdy.lifepath.client.character;

import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.network.s2c.CharacterSyncPayload;
import com.dwurdy.lifepath.platform.ClientOnly;
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
@ClientOnly
public final class ClientCharacterState {
	private static volatile PlayerCharacterData snapshot;
	/** Server-resolved display strings (M6-1) — ids stay in the snapshot. */
	private static volatile com.dwurdy.lifepath.network.s2c
			.IdentitySummaryPayload identity = com.dwurdy.lifepath
			.network.s2c.IdentitySummaryPayload.empty();
	/** Server-resolved per-skill display cards (M6-2). */
	private static volatile java.util.List<com.dwurdy.lifepath.network
			.s2c.SkillsSummaryPayload.SkillCard> skills = java.util.List.of();
	/** Resource → current band index per the last delta (-1 removed). Server defs aren't client-visible. */
	private static final java.util.Map<net.minecraft.resources.ResourceLocation, Integer>
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
		identity = com.dwurdy.lifepath.network.s2c
				.IdentitySummaryPayload.empty();
		skills = java.util.List.of();
		RESOURCE_BANDS.clear();
	}

	/** Applies the server's per-skill display cards (M6-2). */
	public static void applySkills(com.dwurdy.lifepath.network.s2c
			.SkillsSummaryPayload payload) {
		skills = payload.skills();
	}

	/** Latest per-skill display cards — empty until first sync. */
	public static java.util.List<com.dwurdy.lifepath.network.s2c
			.SkillsSummaryPayload.SkillCard> skills() {
		return skills;
	}

	/** Applies the server's resolved display strings (M6-1). */
	public static void applyIdentity(com.dwurdy.lifepath.network.s2c
			.IdentitySummaryPayload payload) {
		identity = payload;
	}

	/** Latest identity summary — never null (empty until first sync). */
	public static com.dwurdy.lifepath.network.s2c
			.IdentitySummaryPayload identity() {
		return identity;
	}

	/**
	 * Applies a resource delta (M4-5 {@code ResourceUpdatePayload}) to the
	 * read-model snapshot + records the band index for M6 feedback.
	 * No-op before the first snapshot.
	 */
	public static void applyResource(net.minecraft.resources.ResourceLocation resourceId,
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
	public static int resourceBand(net.minecraft.resources.ResourceLocation resourceId) {
		return RESOURCE_BANDS.getOrDefault(resourceId, -1);
	}

	/** All known resource band indices (M6-3 HUD) — read-only view. */
	public static java.util.Map<net.minecraft.resources.ResourceLocation, Integer> resourceBands() {
		return java.util.Collections.unmodifiableMap(RESOURCE_BANDS);
	}

	/**
	 * Applies a single-cooldown delta (M4-4 {@code CooldownUpdatePayload}) to
	 * the read-model snapshot so HUD state stays fresh between full syncs.
	 * {@code expiryEpochMs <= 0} clears the entry. No-op before first snapshot.
	 */
	public static void applyCooldown(net.minecraft.resources.ResourceLocation abilityId,
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
