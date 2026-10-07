package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a smithing activity when a player takes output from a smithing table
 * (equipment upgrade/trim recipes).
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingScreenHandlerMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"))
	private void lifepath$smithingOutput(Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onForgeTake(player, stack,
				com.dwurdy.lifepath.LifepathMod.id("smithing_table"));
	}
}
