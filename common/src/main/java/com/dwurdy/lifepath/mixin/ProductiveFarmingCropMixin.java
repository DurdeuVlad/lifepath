package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Productive Farming right-click crop-harvest seam (LP-INT-2), gated by
 * {@code lifepath.compat.productivefarming.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>PF's signature loop is right-click harvesting: {@code
 * ProductiveCropBlock.useWithoutItem} runs the harvest (verified against
 * productivefarming 0.5.3 bytecode — {@code isMaxAge} gate, then
 * {@code getHarvestItemStack} + age reset) and returns a consumed result
 * only when the crop actually popped. An explicit RETURN seam emits
 * exactly on that success — covering every {@code ProductiveCropBlock}
 * subclass (crop, grain, trellis/vine leaf, berry, herb, water crop)
 * without enumerating ~170 block ids into {@code lifepath:click_harvest}.
 *
 * <p>{@code click_harvest} is deliberately NOT bound for PF: the generic
 * {@code onUseBlock} seam emits on the attempt, so tagging
 * {@code FencedStemBlock}/{@code AttachedFencedStemBlock} (which carry an
 * {@code age} property but no right-click harvest) would pay XP on
 * right-click spam without any crop popping.
 */
@Pseudo
@Mixin(targets = "cy.jdkdigital.productivefarming.common.block.ProductiveCropBlock",
		remap = false)
public abstract class ProductiveFarmingCropMixin {

	@Inject(method = "useWithoutItem", at = @At("RETURN"), require = 0)
	private void lifepath$pfHarvested(BlockState state, Level level,
			BlockPos pos, Player player, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction()) {
			VanillaGameplayProducers.onExternalHarvest(player, state);
		}
	}
}
