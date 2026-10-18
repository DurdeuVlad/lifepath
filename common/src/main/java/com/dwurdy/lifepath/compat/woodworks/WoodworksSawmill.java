package com.dwurdy.lifepath.compat.woodworks;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Emit helper for the Woodworks sawmill take. Free of woodworks types — the
 * mixin hands over the vanilla {@code onTake} signature only. Sawing is
 * station crafting (single-input recipe list, stonecutter-shaped), so the
 * emit is CRAFTING with the taken product as subject. The workstation id
 * rides as a matchable tag alongside the {@code lifepath:sawmilled} marker
 * that {@code xp_source/sawmilling.json} requires.
 */
public final class WoodworksSawmill {
	private WoodworksSawmill() {
	}

	private static final ResourceLocation SAWMILL =
			ResourceLocation.fromNamespaceAndPath("woodworks", "sawmill");

	/** Marker {@code xp_source/sawmilling.json} requires — separates sawmill
	 * takes from generic crafting. */
	private static final ResourceLocation SAWMILLED = LifepathMod.id("sawmilled");

	public static void onSawmillTake(Player player, ItemStack stack) {
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(serverPlayer)
				|| stack.isEmpty()) {
			return;
		}
		Set<ResourceLocation> tags = stack.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		tags.add(SAWMILL);
		tags.add(SAWMILLED);
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		ActivityDispatcher.publish(new ActivityEvent(serverPlayer, ActivityTypes.CRAFTING,
				itemId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(),
				Map.of("count", Integer.toString(stack.getCount()),
						"station", SAWMILL.toString())));
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
