package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Furnace/smoker/blast-furnace output takes. Routed through
 * {@link VanillaGameplayProducers#onFurnaceCookOutput}, which gates to
 * edible outputs so smelting ores doesn't feed crafting/cooking XP while
 * cooked food does — closing the furnace gap in the cooking loop.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("TAIL"))
	private void lifepath$furnaceOutput(Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onFurnaceCookOutput(player, stack);
	}
}
