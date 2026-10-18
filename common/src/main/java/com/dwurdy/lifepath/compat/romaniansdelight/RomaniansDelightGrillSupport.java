package com.dwurdy.lifepath.compat.romaniansdelight;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Placer-attribution support for the RomaniansDelight grill — the same
 * delayed-completion convention as {@code KaleidoscopeTavernBarrelTapMixin}'s
 * tap-starter map (pack policy D4 — the attending operator). The grill is a
 * campfire clone: {@code placeFood} loads raw food into the first free slot
 * and {@code cookTick} later pops the finished result as an unattributed
 * world drop, so the player is only in scope at placement. Placer UUIDs are
 * queued per grill position and consumed in order as cooked drops fire —
 * the grill fills slots and completes them in placement order. Non-player
 * placers are never recorded, so automation earns nothing. Entries are not
 * persisted; a chunk reload simply pays nothing for queued food.
 */
public final class RomaniansDelightGrillSupport {

	private static final int MAX_TRACKED_POSITIONS = 64;
	private static final int MAX_PLACERS_PER_GRILL = 8;

	private static final Map<GlobalPos, Deque<UUID>> PLACERS = new HashMap<>();

	private RomaniansDelightGrillSupport() {
	}

	/**
	 * Called when {@code GrillBlockEntity.placeFood(Entity, ItemStack, int)}
	 * accepted food — the entity is the actor who loaded the grill.
	 * {@code grill} is the block entity ({@code this} from the mixin);
	 * level/position are read reflectively.
	 */
	public static void recordPlacer(Object grill, Entity placer) {
		if (!(placer instanceof ServerPlayer player) || isAutomation(player)) {
			return;
		}
		Level level = levelOf(grill);
		BlockPos pos = posOf(grill);
		if (level == null || pos == null || level.isClientSide()) {
			return;
		}
		if (PLACERS.size() > MAX_TRACKED_POSITIONS) {
			PLACERS.clear();
		}
		Deque<UUID> queue = PLACERS.computeIfAbsent(
				GlobalPos.of(level.dimension(), pos.immutable()),
				key -> new ArrayDeque<>());
		if (queue.size() >= MAX_PLACERS_PER_GRILL) {
			queue.pollFirst();
		}
		queue.addLast(player.getUUID());
	}

	/**
	 * Called after each {@code Containers.dropItemStack} inside
	 * {@code GrillBlockEntity.cookTick} — emits the crafting activity for
	 * the cooked stack to the earliest unmatched placer still on the
	 * server. An empty queue means the food was never player-placed (or the
	 * placer data was lost) and the drop earns nothing.
	 */
	public static void emitCookedDrop(Level level, BlockPos pos, ItemStack stack) {
		if (level.isClientSide() || stack == null || stack.isEmpty()) {
			return;
		}
		GlobalPos key = GlobalPos.of(level.dimension(), pos);
		Deque<UUID> queue = PLACERS.get(key);
		if (queue == null) {
			return;
		}
		UUID placer = queue.poll();
		if (queue.isEmpty()) {
			PLACERS.remove(key);
		}
		if (placer == null) {
			return;
		}
		Player player = level.getPlayerByUUID(placer);
		if (player != null) {
			VanillaGameplayProducers.onCraftOutput(player, stack);
		}
	}

	@Nullable
	private static Level levelOf(Object grill) {
		try {
			Object level = grill.getClass().getMethod("getLevel").invoke(grill);
			return level instanceof Level l ? l : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			debug(e, grill, "getLevel");
			return null;
		}
	}

	@Nullable
	private static BlockPos posOf(Object grill) {
		try {
			Object pos = grill.getClass().getMethod("getBlockPos").invoke(grill);
			return pos instanceof BlockPos p ? p : null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			debug(e, grill, "getBlockPos");
			return null;
		}
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}

	private static void debug(Exception e, Object be, String method) {
		LifepathMod.LOGGER.debug("RomaniansDelight grill support: {}.{} reflective call failed: {}",
				be.getClass().getSimpleName(), method, e.toString());
	}
}
