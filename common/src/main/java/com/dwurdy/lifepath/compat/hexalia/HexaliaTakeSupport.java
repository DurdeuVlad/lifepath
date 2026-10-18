package com.dwurdy.lifepath.compat.hexalia;

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
 * Reflective helpers for the Hexalia station take-out mixins — the same
 * degrade-to-debug convention as the Kaleidoscope compat layer (common has no
 * Hexalia classes on its compile classpath, so member access is reflective;
 * a Hexalia update that renames internals degrades to a debug log instead of
 * a hard failure).
 */
public final class HexaliaTakeSupport {

	private HexaliaTakeSupport() {
	}

	/**
	 * Reads an {@link ItemStack} field reflectively and returns a defensive
	 * copy. {@code SmallCauldronContents} exposes its finished brew only as
	 * the private {@code mixtureResult} field (verified against hexalia
	 * 1.3.7 bytecode — {@code getMixtureBaseColor} is the only public accessor
	 * and returns an int).
	 */
	public static ItemStack readFieldStack(Object target, String fieldName) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			Object value = field.get(target);
			return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LifepathMod.LOGGER.debug("Hexalia take support: {}.{} reflective field read failed: {}",
					target.getClass().getSimpleName(), fieldName, e.toString());
			return ItemStack.EMPTY;
		}
	}

	/**
	 * Brewing-activity take-out for station brews that carry no vanilla
	 * {@code POTION_CONTENTS} component — Hexalia cauldron mixtures are custom
	 * {@code BrewItem} stacks, so {@code onBrewingOutput}'s potion-component
	 * gate would silently drop them. Emits the same {@code lifepath:brewing}
	 * event shape (item-id subject + {@code lifepath:brewed} marker tag) minus
	 * the {@code potion/<id>} tag, then runs brewing outcome rules on the
	 * snapshot — same convention as the Kaleidoscope take support, where the
	 * outcome-scaled copy is best-effort since the delivered stack was
	 * already copied from the field.
	 */
	public static void emitBrewedTake(LivingEntity taker, @Nullable ItemStack stack) {
		if (!(taker instanceof ServerPlayer player) || stack == null || stack.isEmpty()) {
			return;
		}
		if (Platform.isInitialized() && Platform.get().isAutomation(player)) {
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
}
