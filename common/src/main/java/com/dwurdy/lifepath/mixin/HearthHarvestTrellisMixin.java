package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hearth &amp; Harvest trellis-planting seam (LP-INT-2), gated by
 * {@code lifepath.compat.hearthandharvest.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code TrellisBlock.useItemOn → applyPlant} folds a held plant item
 * (grape etc.) into the trellis's block-state {@code PLANT} property — no
 * {@code BlockItem.place} ever runs, so the vanilla placement seam stays
 * blind. A consumed {@code applyPlant} = one planting →
 * {@code lifepath:planted} on the post-apply trellis state (its own id, in
 * {@code minecraft:crops} per HH's shipped tag).
 *
 * <p>Only the harvest-irrelevant plant is emitted here — grape harvest is
 * paid by {@code HearthHarvestCropMixin}'s {@code harvestBlock} seam.
 */
@Pseudo
@Mixin(targets = "alabaster.hearthandharvest.common.block.trellis.TrellisBlock",
		remap = false)
public abstract class HearthHarvestTrellisMixin {

	@Inject(method = "applyPlant", at = @At("RETURN"), require = 0)
	private void lifepath$hhTrellisPlanted(ItemStack stack, BlockState state,
			Level level, BlockPos pos, Player player, @Coerce Object plant,
			CallbackInfoReturnable<ItemInteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()
				|| !(player instanceof ServerPlayer sp)
				|| !(level instanceof ServerLevel world)) {
			return;
		}
		VanillaGameplayProducers.onCompatPlanted(sp, world, pos,
				world.getBlockState(pos));
	}
}
