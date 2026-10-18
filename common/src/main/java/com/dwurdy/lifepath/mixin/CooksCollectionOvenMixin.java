package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cook's Collection oven takes → Cooking XP (NeoForge-only, gated by the
 * {@code lifepath.compat.cookscollection.mixins.json} config's
 * {@code requiredMods}). Verified against cookscollection 0.5.7 bytecode:
 * the result slot {@code ModResultSlot extends SlotItemHandler} overrides
 * {@code onTake}, and {@code OvenMenu.quickMoveStack} → {@code Slot.onTake}
 * covers shift-click — the same slot-seam shape as the Farmer's Delight
 * cooking pot, so it emits the same cooking-domain craft activity.
 * Attributed to whoever takes the meal.
 */
@Pseudo
@Mixin(targets = "com.baisylia.cookscollection.block.entity.screen.ModResultSlot",
		remap = false)
public abstract class CooksCollectionOvenMixin {

	@Inject(method = "onTake", at = @At("TAIL"), remap = false, require = 0)
	private void lifepath$ovenTake(Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
