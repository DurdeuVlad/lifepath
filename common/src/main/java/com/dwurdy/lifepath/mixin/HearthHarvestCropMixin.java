package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hearth &amp; Harvest crop-harvest seam (LP-INT-2), gated by
 * {@code lifepath.compat.hearthandharvest.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>HH's tall/multiblock crops harvest on right-click through their own
 * {@code IHarvestable.harvestBlock} funnel — corn stalk
 * ({@code useItemOn → handleHarvestInteraction → harvestBlock}) and grape
 * trellis ({@code useItemOn → harvestBlock}) both resolve there. The generic
 * {@code lifepath:click_harvest} right-click seam can't see these calls
 * (corn needs shears/an item, so {@code useWithoutItem} never runs; the
 * trellis is a multi-block plant with no maturity property), and the break
 * path is unreliable on the trellis (the plant lives in a block-state
 * property, not an {@code age} int).
 *
 * <p>{@code harvestBlock} only runs when the harvest actually happened
 * (its own {@code isHarvestReady} gate precedes it), so a completed call =
 * one harvest → {@link VanillaGameplayProducers#onExternalHarvest} emits
 * {@code lifepath:harvested}+{@code lifepath:mature}, farmable-gated on the
 * crop's {@code minecraft:crops} membership which HH ships for both blocks.
 */
@Pseudo
@Mixin(targets = {
		"alabaster.hearthandharvest.common.block.CornStalkBlock",
		"alabaster.hearthandharvest.common.block.trellis.GrapeTrellisBlock"},
		remap = false)
public abstract class HearthHarvestCropMixin {

	@Inject(method = "harvestBlock", at = @At("RETURN"), require = 0)
	private void lifepath$hhHarvested(BlockState state, Level level, BlockPos pos,
			Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onExternalHarvest(player, state);
	}
}
