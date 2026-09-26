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
 */
public final class CharacterManager {
	private static final int DEFAULT_FLUSH_INTERVAL_TICKS = 6000;
	private static final int MIN_FLUSH_INTERVAL_TICKS = 200;

	private static final Map<UUID, PlayerCharacterData> CACHE = new ConcurrentHashMap<>();
	private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
	private static int ticksSinceFlush;

	private CharacterManager() {
	}

	/** Registers lifecycle hooks. Called once during common mod init. */
	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				initializeCharacter(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			saveCharacter(player);
			CACHE.remove(player.getUuid());
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

	/** Loads (or reloads) the player's data into the cache and syncs the client. */
	public static void initializeCharacter(ServerPlayerEntity player) {
		CACHE.put(player.getUuid(), CharacterAttachments.get(player));
		syncCharacter(player);
	}

	/**
	 * Returns the player's cached character data, lazy-loading it from the
	 * attachment on first access. The returned object is the live model —
	 * mutate it, then {@link #markDirty}.
	 */
	public static PlayerCharacterData getCharacter(ServerPlayerEntity player) {
		return CACHE.computeIfAbsent(player.getUuid(), uuid -> CharacterAttachments.get(player));
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

	/** Writes the cached data back to the persistent attachment. No-op if not cached. */
	public static void saveCharacter(ServerPlayerEntity player) {
		PlayerCharacterData data = CACHE.get(player.getUuid());
		if (data != null) {
			CharacterAttachments.set(player, data);
			DIRTY.remove(player.getUuid());
		}
	}

	/** Sends a full bounded snapshot of the player's synced state to their client. */
	public static void syncCharacter(ServerPlayerEntity player) {
		PlayerCharacterData data = getCharacter(player);
		ServerPlayNetworking.send(player,
				new CharacterSyncPayload(snapshotForSync(data, System.currentTimeMillis())));
	}

	/**
	 * Deep-copies {@code data} for the wire and prunes expired cooldowns from
	 * the copy (the server model keeps them until they naturally clear — the
	 * snapshot must show only active cooldowns). Codec round-trip is used so
	 * the copy can never share mutable state with the cache.
	 */
	static PlayerCharacterData snapshotForSync(PlayerCharacterData data, long nowMillis) {
		PlayerCharacterData copy = CharacterPersistence.deserialize(
				CharacterPersistence.serialize(data));
		copy.clearExpiredCooldowns(nowMillis);
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
