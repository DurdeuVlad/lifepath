package com.dwurdy.lifepath.compat.zetter;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Emit helpers for Zetter's painting seams. Artist-table combine/split takes
 * go through the vanilla crafting-output producer (taken stack in hand —
 * subject = {@code zetter:canvas}). Easel signing produces the
 * {@code zetter:painting} item inside {@code ServerHandler.processSignPainting}
 * with no stack exposed to the mixin, so the produced id is emitted directly
 * with the painting item's own tag set.
 */
public final class ZetterArtistry {
	private ZetterArtistry() {
	}

	private static final ResourceLocation PAINTING =
			ResourceLocation.fromNamespaceAndPath("zetter", "painting");

	/**
	 * Artist-table canvas take-out (combined result or a split piece). The
	 * taken stack's item tags carry the scholarly routing.
	 */
	public static void emitTake(Player player, ItemStack stack) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}

	/**
	 * Canvas→painting signing — reached the {@code Inventory.setItem} line
	 * that installs the produced painting, so the sign is known-committed.
	 */
	public static void emitPaintingSigned(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)
				|| isAutomation(serverPlayer)) {
			return;
		}
		ActivityDispatcher.publish(ActivityEvents.crafting(serverPlayer, PAINTING,
				itemTags(PAINTING), Map.of("seam", "easel_sign"),
				ActivityEvent.Cause.PLAYER));
	}

	private static Set<ResourceLocation> itemTags(ResourceLocation itemId) {
		Item item = BuiltInRegistries.ITEM.get(itemId);
		if (item == null || item == Items.AIR) {
			return Set.of();
		}
		return new ItemStack(item).getTags().map(TagKey::location)
				.collect(Collectors.toSet());
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
