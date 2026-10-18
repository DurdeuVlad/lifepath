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
 * Hearth &amp; Harvest stomping-basin take-out seam (LP-INT-2), gated by
 * {@code lifepath.compat.hearthandharvest.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code StompingBasinBlockEntity.giveOrDrop} is the funnel behind both
 * {@code extractOne} and {@code extractAll} — every processed stack the
 * player pulls out of the basin passes through it. Emitting crafting on the
 * take-out (not on {@code tryProcess}) keeps unattended stomping unpaid:
 * entities wandering onto a basin process inputs too, but only a real
 * player calls {@code extract*}.
 */
@Pseudo
@Mixin(targets = "alabaster.hearthandharvest.common.block.entity.StompingBasinBlockEntity",
		remap = false)
public abstract class HearthHarvestBasinMixin {

	@Inject(method = "giveOrDrop", at = @At("RETURN"), require = 0)
	private void lifepath$hhBasinTakeOut(Player player, ItemStack stack,
			CallbackInfo ci) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
