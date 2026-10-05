package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a crafting activity when a player takes the result stack from a
 * crafting slot (crafting table, inventory grid, crafter UI). Fires only on
 * manual player extraction — crafter/hopper automation never calls this path.
 */
@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"))
	private void lifepath$craftingOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
