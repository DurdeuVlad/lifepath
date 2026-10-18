package com.dwurdy.lifepath.compat.potionbundles;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.platform.Platform;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Emit helper for the PotionBundles bundling take. Bundling is a crafting-grid
 * {@code CustomRecipe} (string + three potions), so the take already pays the
 * generic CRAFTING emit via {@code CraftingResultSlotMixin}. This adds the
 * brewing FACET — the same dual-emit precedent as combat+archery on a
 * projectile kill: one act, two activities, xp_source data weights each.
 * The bundle items carry {@code POTION_CONTENTS}, so the event shape mirrors
 * {@code VanillaGameplayProducers.onBrewingOutput} ({@code lifepath:brewed}
 * plus {@code lifepath:potion/<effect_id>}).
 *
 * <p>Free of potionbundles types: a bundle stack is recognised by the
 * {@code potionbundles} item-id namespace, so a rename of the item classes
 * changes nothing here.
 */
public final class PotionBundleBrewing {
	private PotionBundleBrewing() {
	}

	public static void onBundleTake(Player player, ItemStack stack) {
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(serverPlayer)
				|| stack.isEmpty()) {
			return;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!"potionbundles".equals(itemId.getNamespace())) {
			return;
		}
		Set<ResourceLocation> itemTags = stack.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		itemTags.add(LifepathMod.id("brewed"));
		var contents = stack.get(
				net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		if (contents != null) {
			contents.potion().flatMap(h -> h.unwrapKey())
					.ifPresent(key -> itemTags.add(
							LifepathMod.id("potion/" + key.location().getPath())));
		}
		ActivityDispatcher.publish(ActivityEvents.brewing(
				serverPlayer, itemId, itemTags, ActivityEvent.Cause.PLAYER));
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
