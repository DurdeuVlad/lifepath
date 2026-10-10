package com.dwurdy.lifepath.neoforge;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;

/**
 * RightClickHarvest compat bridge (NeoForge-only, mod-presence gated).
 * RCH harvests crops <em>without</em> breaking the block, so neither the
 * block-break path nor the {@code click_harvest} right-click path sees the
 * harvest — every RCH harvest was silently paying zero Farming XP.
 * RCH exposes {@code RightClickHarvestNeoForgeEvents$AfterHarvest} carrying a
 * {@code HarvestContext(player, block)}; subscription and member access are
 * reflective so an RCH update that renames internals degrades to a debug log.
 * Maturity is guaranteed by the event contract (it only fires post-harvest).
 */
final class RightClickHarvestBridge {

	private RightClickHarvestBridge() {
	}

	@SuppressWarnings("unchecked")
	static void register() {
		try {
			Class<?> eventClass = Class.forName(
					"io.github.jamalam360.rightclickharvest.neoforge"
							+ ".RightClickHarvestNeoForgeEvents$AfterHarvest");
			NeoForge.EVENT_BUS.addListener(
					(Class<? extends net.neoforged.bus.api.Event>) eventClass,
					RightClickHarvestBridge::onAfterHarvest);
			LifepathMod.LOGGER.info("Lifepath: RightClickHarvest AfterHarvest seam wired");
		} catch (ClassNotFoundException | ClassCastException e) {
			LifepathMod.LOGGER.debug(
					"RightClickHarvest bridge degraded: {}", e.toString());
		}
	}

	private static void onAfterHarvest(net.neoforged.bus.api.Event event) {
		try {
			Object ctx = event.getClass().getMethod("getContext").invoke(event);
			Object player = ctx.getClass().getMethod("player").invoke(ctx);
			Object block = ctx.getClass().getMethod("block").invoke(ctx);
			if (player instanceof Player p && block instanceof Block b) {
				VanillaGameplayProducers.onExternalHarvest(p, b.defaultBlockState());
			}
		} catch (ReflectiveOperationException | ClassCastException e) {
			LifepathMod.LOGGER.debug(
					"RightClickHarvest AfterHarvest handler degraded: {}", e.toString());
		}
	}
}
