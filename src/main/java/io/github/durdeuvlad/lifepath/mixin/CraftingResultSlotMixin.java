package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a crafting activity when a player takes the result stack from a
 * crafting slot (crafting table, inventory grid, crafter UI). Fires only on
 * manual player extraction — crafter/hopper automation never calls this path.
 */
@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {

	@Inject(method = "onTakeItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V",
			at = @At("HEAD"))
	private void lifepath$craftingOutput(PlayerEntity player, ItemStack stack,
			CallbackInfo ci) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
