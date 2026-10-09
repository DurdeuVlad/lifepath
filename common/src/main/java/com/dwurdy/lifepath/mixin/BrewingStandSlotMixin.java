package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brewing-stand potion takes → brewing activity (M24 Brewer). Vanilla brewing
 * has no result-slot seam of its own — {@code Slot.onTake} covers the three
 * potion output slots (indices 0–2) of the brewing stand's container, so the
 * guard is "slot lives in a BrewingStandBlockEntity + slot is an output slot
 * + stack is a potion". Inserting-then-taking your own potions pays the
 * trickle XP any take does — the same approximation vanilla furnaces make,
 * and diminishing-returns throttles churn.
 */
@Mixin(Slot.class)
public abstract class BrewingStandSlotMixin {
	@Shadow
	@Final
	public net.minecraft.world.Container container;

	@Shadow
	@Final
	public int slot;

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("TAIL"))
	private void lifepath$brewingOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		if (slot > 2 || !(container instanceof BrewingStandBlockEntity)) {
			return;
		}
		VanillaGameplayProducers.onBrewingOutput(player, stack);
	}
}
