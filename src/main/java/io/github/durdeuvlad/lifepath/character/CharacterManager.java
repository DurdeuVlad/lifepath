package io.github.durdeuvlad.lifepath.character;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.persistence.CharacterPersistence;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.network.s2c.CharacterSyncPayload;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Single authoritative access point for {@link PlayerCharacterData} on the
 * server. Feature code MUST go through this service — nothing else touches
 * {@link CharacterAttachments}/{@link CharacterPersistence} for player state.
 *
 * <p><b>In-memory model:</b> each online player's data is cached by UUID and is
 * the live working copy — callers mutate the object returned by
 * {@link #getCharacter} and then call {@link #markDirty} (and
 * {@link #syncCharacter} when synced fields changed). The cache is written
 * back to the persistent attachment lazily: on disconnect, on server stop,
 * and periodically every {@code character.flush_interval_ticks} ticks
 * (config, default 6000) — so saves are never per-tick.
 *
 * <p><b>Lifecycle:</b> join → load into cache + sync; disconnect → flush +
 * evict; respawn and dimension change → re-sync (the {@code copyOnDeath}
 * attachment travels with the entity; the cached model — including unflushed
 * mutations — survives respawn because it is keyed by UUID, not entity).
 *
 * <p><b>Sync:</b> {@link #syncCharacter} sends a bounded full snapshot of the
 * UI-needed state (expired cooldowns are pruned from the snapshot copy only).
 * There is deliberately no C2S mutation channel.
 *
 * <p><b>Threading:</b> all public methods must be called on the server main
 * thread — the same thread that fires every lifecycle hook registered here.
 * The map is concurrent only for structural safety; the model objects and
 * entity attachments it guards are NOT thread-safe.
 */
public final class CharacterManager {
	/** Default for {@code character.flush_interval_ticks}; referenced by the config spec. */
	public static final int DEFAULT_FLUSH_INTERVAL_TICKS = 6000;
	private static final int MIN_FLUSH_INTERVAL_TICKS = 200;

	private static final Map<UUID, CachedCharacter> CACHE = new ConcurrentHashMap<>();
	private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
	private static int ticksSinceFlush;

	private CharacterManager() {
	}

	/**
	 * Cache entry. {@code owner} tracks WHICH entity currently owns this data —
	 * a UUID can briefly own two sessions during a duplicate/zombie login (the
	 * new player entity is constructed before the old one's disconnect cleanup
	 * runs). Keeping data keyed to its owner prevents an old session's
	 * disconnect from evicting or overwriting the new session's live state.
	 */
	private static final class CachedCharacter {
		private ServerPlayerEntity owner;
		private final PlayerCharacterData data;

		private CachedCharacter(ServerPlayerEntity owner, PlayerCharacterData data) {
			this.owner = owner;
			this.data = data;
		}
	}

	/** Registers lifecycle hooks. Called once during common mod init. */
	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				initializeCharacter(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			saveCharacter(player);
			// Evict only if THIS session still owns the entry — a stale/zombie
			// session must not remove a newer session's cache.
			CACHE.computeIfPresent(player.getUuid(), (uuid, entry) ->
					entry.owner == player ? null : entry);
			DIRTY.remove(player.getUuid());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				syncCharacter(newPlayer));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
				syncCharacter(player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++ticksSinceFlush >= flushIntervalTicks()) {
				ticksSinceFlush = 0;
				flushDirty(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			flushDirty(server);
			CACHE.clear();
			DIRTY.clear();
		});
	}

	/**
	 * Loads (or reloads) the player's data into the cache and syncs the client.
	 * If an entry already exists for this UUID (e.g. duplicate login while a
	 * zombie session is still winding down), the EXISTING cached data is kept —
	 * it may hold unflushed mutations that are fresher than what was just read
	 * from disk — and ownership transfers to the new entity.
	 */
	public static void initializeCharacter(ServerPlayerEntity player) {
		CACHE.compute(player.getUuid(), (uuid, entry) -> {
			if (entry == null) {
				return new CachedCharacter(player, CharacterAttachments.get(player));
			}
			entry.owner = player;
			return entry;
		});
		// Lazy decay trigger (M3-3): offline elapsed time is charged once here —
		// deterministic because the window anchors on persisted timestamps.
		if (io.github.durdeuvlad.lifepath.skill.SkillDecayService.applyLazyAll(
				getCharacter(player), System.currentTimeMillis()) > 0) {
			markDirty(player);
		}
		syncCharacter(player);
	}

	/**
	 * Returns the player's cached character data, lazy-loading it from the
	 * attachment on first access. The returned object is the live model —
	 * mutate it, then {@link #markDirty}. Call on the server thread only, and
	 * only for players currently connected (an offline entity would leak a
	 * cache entry with no eviction path).
	 */
	public static PlayerCharacterData getCharacter(ServerPlayerEntity player) {
		return CACHE.computeIfAbsent(player.getUuid(),
				uuid -> new CachedCharacter(player, CharacterAttachments.get(player))).data;
	}

	/** Flags the player's cached data as modified; persisted at the next flush point. */
	public static void markDirty(ServerPlayerEntity player) {
		DIRTY.add(player.getUuid());
	}

	/**
	 * Convenience for mutation call sites: marks dirty and re-syncs the client
	 * in one call (synced fields changed ⇒ client must see them).
	 */
	public static void changed(ServerPlayerEntity player) {
		markDirty(player);
		syncCharacter(player);
	}

	/**
	 * Writes the cached data back to the persistent attachment. No-op if the
	 * player no longer owns the cache entry (e.g. a zombie session flushing
	 * after a duplicate login already transferred ownership).
	 */
	public static void saveCharacter(ServerPlayerEntity player) {
		CachedCharacter entry = CACHE.get(player.getUuid());
		if (entry != null && entry.owner == player) {
			CharacterAttachments.set(player, entry.data);
			DIRTY.remove(player.getUuid());
		}
	}

	/** Sends a full bounded snapshot of the player's synced state to their client. */
	public static void syncCharacter(ServerPlayerEntity player) {
		PlayerCharacterData data = getCharacter(player);
		try {
			ServerPlayNetworking.send(player,
					new CharacterSyncPayload(snapshotForSync(data, System.currentTimeMillis())));
			// M6-1: display strings ride the same funnel so identity text is
			// fresh after join/respawn/dimension change/mutation.
			ServerPlayNetworking.send(player, IdentitySummary.build(data));
			// M6-2: per-skill display cards ride the same funnel.
			ServerPlayNetworking.send(player,
					io.github.durdeuvlad.lifepath.skill.SkillSummary.build(data,
							System.currentTimeMillis()));
		} catch (Exception e) {
			LifepathMod.LOGGER.error("failed to send character sync to {}", player.getUuid(), e);
		}
	}

	/**
	 * Deep-copies {@code data} for the wire and prunes expired cooldowns from
	 * the copy (the server model keeps them until they naturally clear — the
	 * snapshot must show only active cooldowns). Codec round-trip is used so
	 * the copy can never share mutable state with the cache.
	 */
	static PlayerCharacterData snapshotForSync(PlayerCharacterData data, long nowMillis) {
		PlayerCharacterData copy = CharacterPersistence.copy(data);
		copy.clearExpiredCooldowns(nowMillis);
		// Passive-schedule markers are server bookkeeping, not cooldowns —
		// a HUD iterating cooldowns must never see them (M4-4).
		copy.cooldowns().keySet().stream()
				.filter(io.github.durdeuvlad.lifepath.ability.CooldownService::isScheduleKey)
				.toList().forEach(copy::removeCooldown);
		// The anti-exploit ledger is server-only bookkeeping - never synced.
		copy.clearActionSignatures();
		return copy;
	}

	private static void flushDirty(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (DIRTY.contains(player.getUuid())) {
				saveCharacter(player);
			}
		}
	}

	private static int flushIntervalTicks() {
		return Math.max(MIN_FLUSH_INTERVAL_TICKS,
				LifepathConfig.getInt(LifepathMod.id("character"), "flush_interval_ticks"));
	}
}
