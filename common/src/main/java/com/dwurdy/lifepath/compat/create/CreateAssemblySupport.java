package com.dwurdy.lifepath.compat.create;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.platform.Platform;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Emit helper for Create contraption assembly/deploy acts (driver-block
 * "assemble now" clicks, train assembly at a station, minecart-contraption
 * placement). Free of Create types — the mixins hand over the resolved
 * player, position, and a {@code create:*} source id only, so this class
 * loads whether or not Create is present.
 *
 * <p>An assembled or deployed contraption IS engineering work: every event
 * carries the {@code lifepath:assembled} marker (the discrete high-value tag
 * {@code xp_source/engineering.json}'s {@code per_tag} weights) plus the
 * {@code lifepath:engineering_blocks} family tag, emitted as a
 * {@code lifepath:crafting} activity so all weighting stays in datapack
 * data. The {@code action} attribute records {@code assemble} vs
 * {@code deploy}, {@code surface} the hook family ({@code driver} /
 * {@code station} / {@code minecart}).
 *
 * <p>Debounced 40 ticks per {@code dim|pos|action}: the driver click only
 * arms the block entity ({@code assembleNextTick = true}), so spam-clicks
 * on an already-armed driver inside the window pay nothing while a real
 * disassemble → reassemble cycle (always longer than the window) still
 * earns each time. Same bounded-map pattern as
 * {@code MaterialGates.notifyDenied}.
 */
public final class CreateAssemblySupport {
	private CreateAssemblySupport() {
	}

	/** Marker {@code xp_source/engineering.json}'s per_tag weights —
	 * separates contraption assembly from the generic crafting trickle. */
	private static final ResourceLocation ASSEMBLED = LifepathMod.id("assembled");

	/** Family tag — the act is engineering work regardless of surface. */
	private static final ResourceLocation ENGINEERING_BLOCKS =
			LifepathMod.id("engineering_blocks");

	/** Re-clicks inside this window on the same pos+action emit nothing. */
	private static final long DEBOUNCE_TICKS = 40;
	private static final Map<String, Long> RECENT = new HashMap<>();

	/**
	 * Driver-block "assemble now" request and station train assembly —
	 * {@code sourceId} is the driver block id or {@code create:track_station},
	 * {@code surface} is {@code "driver"} / {@code "station"}.
	 */
	public static void onAssembleRequest(ServerPlayer player, BlockPos pos,
			ResourceLocation sourceId, String surface) {
		emit(player, pos, sourceId, "assemble", surface);
	}

	/**
	 * Minecart-contraption placement on a rail — {@code sourceId} is the
	 * contraption item id, {@code surface} is {@code "minecart"}.
	 */
	public static void onDeploy(ServerPlayer player, BlockPos pos,
			ResourceLocation sourceId, String surface) {
		emit(player, pos, sourceId, "deploy", surface);
	}

	private static void emit(ServerPlayer player, BlockPos pos,
			ResourceLocation sourceId, String action, String surface) {
		if (isAutomation(player)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		String key = level.dimension().location() + "|" + pos.asLong()
				+ "|" + action;
		long now = level.getGameTime();
		Long last = RECENT.get(key);
		if (last != null && now - last < DEBOUNCE_TICKS) {
			return;
		}
		if (RECENT.size() > 256) {
			RECENT.clear();
		}
		RECENT.put(key, now);
		Set<ResourceLocation> tags = new HashSet<>();
		tags.add(ASSEMBLED);
		tags.add(ENGINEERING_BLOCKS);
		ActivityDispatcher.publish(ActivityEvents.crafting(player, sourceId,
				tags, Map.of("action", action, "surface", surface),
				ActivityEvent.Cause.PLAYER));
	}

	private static boolean isAutomation(ServerPlayer player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
