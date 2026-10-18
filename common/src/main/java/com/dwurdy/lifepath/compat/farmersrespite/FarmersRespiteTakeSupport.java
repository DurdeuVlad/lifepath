package com.dwurdy.lifepath.compat.farmersrespite;

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
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Emit helper for the Farmers Respite kettle takes — free of
 * farmersrespite types, so this class loads without the mod. Emits the
 * {@code lifepath:brewing} activity (kettle-brewed drink) with the
 * {@code lifepath:brewed} marker tag — the same event shape as the
 * Hexalia compat layer's station brews — then runs brewing outcome
 * rules on the stack the taker receives.
 */
public final class FarmersRespiteTakeSupport {

	private FarmersRespiteTakeSupport() {
	}

	/**
	 * Result-slot take from the kettle menu — the stack is the brewed
	 * drink the player removes. Non-player takers (shift-click into
	 * nothing, automation) earn nothing.
	 */
	public static void emitBrewedTake(LivingEntity taker, @Nullable ItemStack stack) {
		if (!(taker instanceof ServerPlayer player) || stack == null || stack.isEmpty()) {
			return;
		}
		if (isAutomation(player)) {
			return;
		}
		emit(player, stack);
	}

	/**
	 * Direct container-pour take from the kettle block: {@code
	 * KettleBlockEntity.fluidExtract} returns the filled stack given to the
	 * player. Only a produced drink pays — the mod's own {@code
	 * isKnownDrinkItem(ItemStack)} (a static on the block entity) gates it,
	 * and plain {@code POTION} items are excluded so drawing hot water into
	 * a bottle earns nothing. A reflective failure stays silent rather
	 * than paying for an ambiguous stack.
	 */
	public static void emitIfDrinkTake(LivingEntity taker, Object kettle,
			@Nullable ItemStack result) {
		if (!(taker instanceof ServerPlayer player) || result == null || result.isEmpty()) {
			return;
		}
		if (isAutomation(player) || result.is(Items.POTION) || !isDrink(kettle, result)) {
			return;
		}
		emit(player, result);
	}

	private static void emit(ServerPlayer player, ItemStack stack) {
		Set<ResourceLocation> itemTags = stack.getTags()
				.map(TagKey::location)
				.collect(Collectors.toCollection(HashSet::new));
		itemTags.add(LifepathMod.id("brewed"));
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		ActivityDispatcher.publish(ActivityEvents.brewing(
				player, itemId, itemTags, ActivityEvent.Cause.PLAYER));
		OutcomeService.apply(player, ActivityTypes.BREWING, itemId, itemTags, stack);
	}

	private static boolean isDrink(Object kettle, ItemStack stack) {
		try {
			Object known = kettle.getClass()
					.getMethod("isKnownDrinkItem", ItemStack.class)
					.invoke(null, stack);
			return Boolean.TRUE.equals(known);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LifepathMod.LOGGER.debug("FarmersRespite kettle support: isKnownDrinkItem reflective call failed: {}",
					e.toString());
			return false;
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
