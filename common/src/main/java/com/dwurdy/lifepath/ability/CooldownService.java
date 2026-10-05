package com.dwurdy.lifepath.ability;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.config.LifepathConfig;
import com.dwurdy.lifepath.network.s2c.CooldownUpdatePayload;
import java.util.ArrayList;
import java.util.List;
import com.dwurdy.lifepath.network.LifepathNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The one authoritative cooldown tracker for all abilities (M4-4, GAMEDESIGN
 * §12). Backed by {@link PlayerCharacterData#cooldowns()} — the generic map on
 * the character model — so cooldowns persist, sanitize, and sync with the rest
 * of character state. <b>No ad-hoc timers:</b> every read/write of that map for
 * ability-timing purposes goes through this service (the passive-schedule
 * markers included — they live under the {@link #SCHEDULE_PREFIX} sentinel).
 *
 * <p><b>Persistence contract:</b> the attachment codec serializes the whole
 * map, but {@code CharacterPersistence.sanitize} drops ability cooldowns whose
 * remaining time at load is &le; {@code abilities.toml persist_min_seconds}
 * (default 5s). Enforcement at the load boundary is the only interception
 * point the codec offers — and it is the correct one: it also retires cooldowns
 * that fully elapsed while the player was offline. Schedule markers are exempt
 * (engine bookkeeping; a stale one just re-fires the sweep).
 *
 * <p><b>Sync contract:</b> the full snapshot (M1-2) already carries active
 * cooldowns; {@code trigger}/{@code clear} additionally send a lightweight
 * {@link CooldownUpdatePayload} delta so HUD state stays fresh between
 * snapshots. Client state is advisory — the server re-checks on every eval.
 *
 * <p><b>Balance:</b> {@code cooldown_multiplier} (abilities.toml) scales every
 * ability's declared cooldown; {@code 0} disables cooldowns globally.
 */
public final class CooldownService {
	private CooldownService() {}

	/**
	 * Prefix for passive-schedule marker keys in the cooldown map:
	 * {@code schedule/<abilityNs>/<abilityPath>}. {@code CharacterPersistence}
	 * treats these as engine bookkeeping — never dropped as unknown abilities.
	 */
	public static final String SCHEDULE_PREFIX = "schedule/";

	/**
	 * True when {@code id} is a passive-schedule marker rather than a real
	 * ability cooldown key — namespace-checked so a foreign content id like
	 * {@code othermod:schedule/x} is never mistaken for engine bookkeeping.
	 */
	public static boolean isScheduleKey(ResourceLocation id) {
		return LifepathMod.MOD_ID.equals(id.getNamespace())
				&& id.getPath().startsWith(SCHEDULE_PREFIX);
	}

	// ------------------------------------------------------------------
	// Data-path cores — testable without a live player; no packets sent.
	// ------------------------------------------------------------------

	/** True when {@code abilityId}'s cooldown has not yet expired at {@code nowMs}. */
	public static boolean isOnCooldown(PlayerCharacterData data, ResourceLocation abilityId, long nowMs) {
		Long expiry = data.cooldowns().get(abilityId);
		return expiry != null && expiry > nowMs;
	}

	/** Milliseconds remaining on {@code abilityId}'s cooldown at {@code nowMs}; 0 when free. */
	public static long remainingMillis(PlayerCharacterData data, ResourceLocation abilityId, long nowMs) {
		Long expiry = data.cooldowns().get(abilityId);
		return expiry == null ? 0L : Math.max(0L, expiry - nowMs);
	}

	/**
	 * Starts {@code abilityId}'s cooldown: {@code cooldownSeconds} × the global
	 * {@code cooldown_multiplier}. An effective duration &le; 0 (or non-finite)
	 * removes the cooldown instead — {@code cooldown_multiplier 0} disables
	 * cooldowns globally.
	 *
	 * @return the expiry timestamp (epoch ms), or {@code -1} when no cooldown
	 *         was set.
	 */
	public static long trigger(PlayerCharacterData data, ResourceLocation abilityId,
			double cooldownSeconds, long nowMs) {
		double multiplier = ((Number) LifepathConfig.getOrDefault(
				LifepathMod.id("abilities"), "cooldown_multiplier", 1.0)).doubleValue();
		double effective = cooldownSeconds * multiplier;
		if (!(effective > 0) || !Double.isFinite(effective)) {
			data.removeCooldown(abilityId);
			return -1L;
		}
		// Saturating add: a datapack can name an absurd duration; an
		// overflowing expiry would wrap negative and act as NO cooldown.
		long expiry = effective * 1000.0 > (double) (Long.MAX_VALUE - nowMs)
				? Long.MAX_VALUE
				: nowMs + (long) (effective * 1000.0);
		data.setCooldown(abilityId, expiry);
		return expiry;
	}

	/**
	 * Player-facing {@link #trigger}: writes the cooldown AND pushes a
	 * {@link CooldownUpdatePayload} delta (the spec's "update packet when
	 * triggered"). Full-snapshot sync stays the caller's contract — the engine
	 * already {@code changed()}es on execution.
	 */
	public static long trigger(ServerPlayer player, ResourceLocation abilityId,
			double cooldownSeconds, long nowMs) {
		long expiry = trigger(CharacterManager.getCharacter(player), abilityId,
				cooldownSeconds, nowMs);
		// A trigger outside an execution path must still persist the stamp.
		CharacterManager.markDirty(player);
		LifepathNetworking.sendTo(player,
				new CooldownUpdatePayload(abilityId, Math.max(0L, expiry)));
		return expiry;
	}

	/**
	 * Removes {@code abilityId}'s cooldown. Schedule markers are never cleared
	 * here — they are engine bookkeeping, not player-visible cooldowns.
	 *
	 * @return true when a cooldown entry existed.
	 */
	public static boolean clear(PlayerCharacterData data, ResourceLocation abilityId) {
		if (isScheduleKey(abilityId)) {
			return false;
		}
		boolean present = data.cooldowns().containsKey(abilityId);
		data.removeCooldown(abilityId);
		return present;
	}

	/** Removes every ability cooldown (schedule markers preserved). Returns the count. */
	public static int clearAll(PlayerCharacterData data) {
		List<ResourceLocation> real = new ArrayList<>();
		for (ResourceLocation id : data.cooldowns().keySet()) {
			if (!isScheduleKey(id)) {
				real.add(id);
			}
		}
		real.forEach(data::removeCooldown);
		return real.size();
	}

	/** Debug-command API (M7-4): clears one cooldown and re-syncs the client. */
	public static boolean clear(ServerPlayer player, ResourceLocation abilityId) {
		boolean cleared = clear(CharacterManager.getCharacter(player), abilityId);
		if (cleared) {
			LifepathNetworking.sendTo(player, new CooldownUpdatePayload(abilityId, 0L));
			CharacterManager.changed(player);
		}
		return cleared;
	}

	/** Debug-command API (M7-4): clears all cooldowns; returns the count removed. */
	public static int clearAll(ServerPlayer player) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		List<ResourceLocation> cleared = new ArrayList<>();
		for (ResourceLocation id : data.cooldowns().keySet()) {
			if (!isScheduleKey(id)) {
				cleared.add(id);
			}
		}
		if (cleared.isEmpty()) {
			return 0;
		}
		cleared.forEach(id -> {
			data.removeCooldown(id);
			LifepathNetworking.sendTo(player, new CooldownUpdatePayload(id, 0L));
		});
		CharacterManager.changed(player);
		return cleared.size();
	}

	// ------------------------------------------------------------------
	// Passive-schedule markers — engine bookkeeping in the same map.
	// ------------------------------------------------------------------

	/**
	 * Cooldown-map key for a passive ability's next-eval marker:
	 * {@code schedule/<ns>/<path>} keeps namespace and path losslessly
	 * distinct (unlike a {@code :}→{@code _} rewrite, which collides e.g.
	 * {@code a:b_c} with {@code a_b:c}).
	 */
	public static ResourceLocation scheduleKey(ResourceLocation abilityId) {
		return ResourceLocation.fromNamespaceAndPath("lifepath",
				SCHEDULE_PREFIX + abilityId.getNamespace() + "/" + abilityId.getPath());
	}

	/** The next-due timestamp (epoch ms) for a passive ability, or null if unscheduled. */
	@Nullable
	public static Long nextDueAt(PlayerCharacterData data, ResourceLocation abilityId) {
		return data.cooldowns().get(scheduleKey(abilityId));
	}

	/** Records the next-due timestamp for a passive ability's eval. */
	public static void markNextDue(PlayerCharacterData data, ResourceLocation abilityId, long dueAtMs) {
		data.setCooldown(scheduleKey(abilityId), dueAtMs);
	}
}
