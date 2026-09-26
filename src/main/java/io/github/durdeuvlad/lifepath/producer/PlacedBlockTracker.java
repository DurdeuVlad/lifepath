package io.github.durdeuvlad.lifepath.producer;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * In-memory record of recently player-placed block positions, per world
 * (M2-4 anti-exploit). A block the player placed awards no mining XP when
 * re-broken — the classic place-and-mine farm.
 *
 * <p><b>Deliberate limits (documented):</b> the tracker is RAM-only (a
 * restart forgives old placements — acceptable; persistence would cost more
 * than the exploit is worth) and bounded per world (oldest entries evict).
 * It covers the cheap case — immediate place/break loops — not long-horizon
 * farming, which diminishing returns (M3-4) is for.
 */
public final class PlacedBlockTracker {
	private PlacedBlockTracker() {
	}

	/** Positions retained per world before oldest-eviction. */
	static final int CAPACITY_PER_WORLD = 4096;

	private record WorldKey(net.minecraft.registry.RegistryKey<World> dim) {
	}

	private static final Map<WorldKey, Map<BlockPos, Boolean>> TRACKED = new LinkedHashMap<>();

	/** Records {@code pos} in {@code world} as player-placed. */
	public static void record(World world, BlockPos pos) {
		record(world.getRegistryKey(), pos);
	}

	/**
	 * Consumes the record: returns true (and clears it) the first time a
	 * recorded position is queried — one placement defeats one break.
	 */
	public static boolean consume(World world, BlockPos pos) {
		return consume(world.getRegistryKey(), pos);
	}

	public static boolean contains(World world, BlockPos pos) {
		return contains(world.getRegistryKey(), pos);
	}

	// RegistryKey-keyed seam: identical semantics, testable without a World.
	static synchronized void record(net.minecraft.registry.RegistryKey<World> key, BlockPos pos) {
		tracked(key).put(pos.toImmutable(), Boolean.TRUE);
	}

	static synchronized boolean consume(net.minecraft.registry.RegistryKey<World> key, BlockPos pos) {
		return tracked(key).remove(pos) != null;
	}

	static synchronized boolean contains(net.minecraft.registry.RegistryKey<World> key, BlockPos pos) {
		return tracked(key).containsKey(pos);
	}

	private static Map<BlockPos, Boolean> tracked(net.minecraft.registry.RegistryKey<World> key) {
		return TRACKED.computeIfAbsent(new WorldKey(key),
				k -> new LinkedHashMap<>(16, 0.75f, true) {
					@Override
					protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
						return size() > CAPACITY_PER_WORLD;
					}
				});
	}

	/** Test hook: clears all tracked positions. Not for production use. */
	public static synchronized void resetForTests() {
		TRACKED.clear();
	}
}
