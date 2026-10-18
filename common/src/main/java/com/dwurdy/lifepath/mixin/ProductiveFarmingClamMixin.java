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
 * Productive Farming clam pearl-harvest seam (LP-INT-2), gated by
 * {@code lifepath.compat.productivefarming.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code ClamBlock.useWithoutItem} pops the grown pearl and replaces the
 * clam with water/air, returning SUCCESS unconditionally on that path — the
 * generic {@code lifepath:click_harvest} right-click seam can't classify
 * maturity on a {@code FaceAttachedHorizontalDirectionalBlock} with no
 * {@code age}/{@code berries} property, so this emit is explicit. It routes
 * through {@link VanillaGameplayProducers#onExternalHarvest}, which keeps
 * the farmable tag gate — datapack adds {@code productivefarming:clam} to
 * {@code lifepath:harvestable} to pay it.
 */
@Pseudo
@Mixin(targets = "cy.jdkdigital.productivefarming.common.block.ClamBlock",
		remap = false)
public abstract class ProductiveFarmingClamMixin {

	@Inject(method = "useWithoutItem", at = @At("RETURN"), require = 0)
	private void lifepath$pfClamHarvested(BlockState state, Level level,
			BlockPos pos, Player player, BlockHitResult hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		// consumesAction covers SUCCESS plus CONSUME/CONSUME_PARTIAL — the
		// mod currently returns SUCCESS, but a sidedSuccess refactor would
		// silently dead the seam on a strict equality check (sibling crop
		// mixin already uses the wider test).
		if (cir.getReturnValue().consumesAction()) {
			VanillaGameplayProducers.onExternalHarvest(player, state);
		}
	}
}
