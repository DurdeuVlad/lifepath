package com.dwurdy.lifepath.compat.forester;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.producer.PlacedBlockTracker;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Support for {@code TimberTalesEventsMixin}: publishes one
 * {@code lifepath:mining} activity when a completed Timber Tales cut queues
 * its tree fell. The block is a log by construction (TT only queues real
 * wood), so the event carries {@code minecraft:logs} as a semantic marker —
 * the same convention {@code onForgeTake} uses for
 * {@code lifepath:smithing_workstations} — letting the woodcutting
 * xp_source's {@code required_tags} gate pass. The coerced BreakEvent is
 * read reflectively; any API drift fails closed with no award. The
 * {@link PlacedBlockTracker} guard replicates the vanilla break path's
 * re-mine rule: a placed base block felled by TT pays nothing.
 */
public final class TimberTalesSupport {

	private TimberTalesSupport() {
	}

	private static final ResourceLocation LOGS =
			ResourceLocation.fromNamespaceAndPath("minecraft", "logs");
	private static final ResourceLocation TT_FELLED = LifepathMod.id("tt_felled");

	public static void onTreeFelled(Object breakEvent) {
		try {
			Player player = (Player) breakEvent.getClass()
					.getMethod("getPlayer").invoke(breakEvent);
			BlockState state = (BlockState) breakEvent.getClass()
					.getMethod("getState").invoke(breakEvent);
			if (!(player instanceof ServerPlayer serverPlayer) || state == null
					|| isAutomation(serverPlayer)) {
				return;
			}
			// Same re-mine guard the vanilla break path applies: a fell
			// whose base block the player placed pays nothing.
			BlockPos pos = (BlockPos) breakEvent.getClass()
					.getMethod("getPos").invoke(breakEvent);
			if (pos != null && PlacedBlockTracker.consume(serverPlayer.level(), pos)) {
				return;
			}
			Set<ResourceLocation> tags = state.getTags()
					.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
			tags.add(LOGS);
			tags.add(TT_FELLED);
			ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
			ActivityDispatcher.publish(new ActivityEvent(serverPlayer, ActivityTypes.MINING,
					blockId, tags, ActivityEvent.Cause.PLAYER, System.currentTimeMillis(),
					Map.of("mod", "timber_tales")));
		} catch (Throwable t) {
			// Foreign API drift / non-BreakEvent argument — fail closed.
		}
	}

	private static boolean isAutomation(ServerPlayer player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
