package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a smithing activity when a player takes output from a smithing table
 * (equipment upgrade/trim recipes), and enforces material gates on the
 * result — the smithing table must not bypass the tier gates the Overgeared
 * anvil enforces.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingScreenHandlerMixin
		extends net.minecraft.world.inventory.ItemCombinerMenu {

	private SmithingScreenHandlerMixin() {
		super(null, 0, null, null);
	}

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"))
	private void lifepath$smithingOutput(Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onForgeTake(player, stack,
				com.dwurdy.lifepath.LifepathMod.id("smithing_table"));
	}

	/**
	 * Vanilla-leak closure: after vanilla computes the transform/trim result,
	 * clear it when a material gate denies it for this player. Clearing the
	 * result slot can't recurse — {@code slotsChanged} only watches the input
	 * container. No blueprint bypass exists at the vanilla smithing table.
	 */
	@Inject(method = "createResult", at = @At("TAIL"))
	private void lifepath$gateSmithingResult(CallbackInfo ci) {
		SmithingMenu self = (SmithingMenu) (Object) this;
		if (!(player instanceof ServerPlayer sp)) {
			return;
		}
		var resultSlot = self.getSlot(SmithingMenu.RESULT_SLOT);
		var denial = com.dwurdy.lifepath.skill.MaterialGates
				.denialFor(sp, resultSlot.getItem(), false);
		if (denial != null) {
			resultSlot.set(ItemStack.EMPTY);
			com.dwurdy.lifepath.skill.MaterialGates.notifyDenied(sp, denial);
		}
	}
}
