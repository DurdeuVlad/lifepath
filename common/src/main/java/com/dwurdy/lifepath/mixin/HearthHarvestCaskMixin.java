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
 * Hearth &amp; Harvest cask take-out seam (LP-INT-2), gated by
 * {@code lifepath.compat.hearthandharvest.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>The aging cask's result slot ({@code CaskResultSlot.onTake}) is the
 * single funnel for aged outputs (wine/cider). Same convention as the
 * crafting-table and Kaleidoscope take-outs: the taken stack is the event
 * surface — {@link VanillaGameplayProducers#onCraftOutput} emits the
 * crafting event and applies outcome scaling; {@code lifepath:cookable_foods}
 * membership in data decides whether it pays cooking XP.
 */
@Pseudo
@Mixin(targets = "alabaster.hearthandharvest.common.block.entity.container.CaskResultSlot",
		remap = false)
public abstract class HearthHarvestCaskMixin {

	@Inject(method = "onTake", at = @At("RETURN"), require = 0)
	private void lifepath$hhCaskTakeOut(Player player, ItemStack stack,
			CallbackInfo ci) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
