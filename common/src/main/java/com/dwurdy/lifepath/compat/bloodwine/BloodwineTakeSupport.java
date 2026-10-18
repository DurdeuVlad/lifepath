package com.dwurdy.lifepath.compat.bloodwine;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.skill.OutcomeService;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Emit helper for the Kaleidoscope Bloodwine drink-block take-out. Free of
 * bloodwine types — the mixin hands over the stack that
 * {@code BloodDrinkBlockEntity.removeItem()} returned, so this class loads
 * without the mod. Emits the {@code lifepath:brewing} activity (barrel-
 * fermented drink) with the {@code lifepath:brewed} marker tag — the same
 * event shape as the Hexalia compat layer's station brews — then runs
 * brewing outcome rules on the stack the taker receives.
 */
public final class BloodwineTakeSupport {

	private BloodwineTakeSupport() {
	}

	/**
	 * Serving take from a placed {@code BloodDrinkBlock}: the removed stack
	 * is the produced wine the player receives. Non-player takers
	 * (dispensers, mobs) earn nothing.
	 */
	public static void emitBrewedTake(LivingEntity taker, @Nullable ItemStack stack) {
		if (!(taker instanceof ServerPlayer player) || stack == null || stack.isEmpty()) {
			return;
		}
		if (isAutomation(player)) {
			return;
		}
		Set<ResourceLocation> itemTags = stack.getTags()
				.map(TagKey::location)
				.collect(Collectors.toCollection(HashSet::new));
		itemTags.add(LifepathMod.id("brewed"));
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		ActivityDispatcher.publish(ActivityEvents.brewing(
				player, itemId, itemTags, ActivityEvent.Cause.PLAYER));
		OutcomeService.apply(player, ActivityTypes.BREWING, itemId, itemTags, stack);
	}

	private static boolean isAutomation(ServerPlayer player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
