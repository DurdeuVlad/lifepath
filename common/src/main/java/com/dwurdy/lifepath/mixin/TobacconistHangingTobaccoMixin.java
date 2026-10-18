package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.tobacconist.TobacconistTakeSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tobacconist hanging-bundle break harvest → crafting XP (NeoForge-only,
 * gated by the {@code lifepath.compat.tobacconistmod.mixins.json} config's
 * {@code requiredMods}). Verified against tobacconistmod 4.0.0 bytecode:
 * {@code HangingTobaccoBlock} has no use-interaction — harvesting is a
 * plain block break. {@code playerWillDestroy(Level, BlockPos, BlockState,
 * Player)} is the last point the breaking player is in scope;
 * {@code onRemove} then runs {@code HangingTobaccoBlockEntity.dropContents},
 * which drops {@code getStoredLeaf()} unconditionally. The emit therefore
 * gates on the BE's {@code isFinished()} (finished bundles carry the cure
 * marker) and on a non-creative break — creative takes the
 * {@code discardContents} path and produces no drop. Attributed to the
 * breaker; non-player destruction never reaches this method.
 */
@Pseudo
@Mixin(targets = "com.diggydwarff.tobacconistmod.block.custom.HangingTobaccoBlock",
		remap = false)
public abstract class TobacconistHangingTobaccoMixin {

	@Inject(method = "playerWillDestroy", at = @At("HEAD"), remap = false,
			require = 0)
	private void lifepath$emitBundleHarvest(Level level, BlockPos pos,
			BlockState state, Player player,
			CallbackInfoReturnable<BlockState> cir) {
		if (level.isClientSide() || player == null || player.isCreative()) {
			return;
		}
		TobacconistTakeSupport.emitBundleHarvest(this, level, pos, state, player);
	}
}
