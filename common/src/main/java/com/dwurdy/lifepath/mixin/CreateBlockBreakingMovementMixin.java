package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.create.CreateAttendedAutomation;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create contraption block-breaks → attended-automation XP (NeoForge-only,
 * gated by the {@code lifepath.compat.create.mixins.json} config's
 * {@code requiredMods}). Verified against Create 6.0.10 bytecode: mounted
 * drills, saws, ploughs, and rollers all destroy through
 * {@code BlockBreakingMovementBehaviour.destroyBlock(context, pos)} — one
 * call per block actually destroyed, including the roller's extra column
 * positions and never posted to {@code BlockEvent.BreakEvent} (Create passes
 * a null player to {@code BlockHelper.destroyBlockAs}). Injecting at HEAD of
 * the shared destroy path catches every break-capable actor in one hook —
 * the block state is still in the world at that point and the destroy
 * itself can no longer be cancelled.
 *
 * <p>{@code onBlockBroken} was rejected as the seam: it fires once per
 * trigger block only (roller columns and saw tree-cuts bypass it), it
 * double-fires via {@code super.onBlockBroken} for roller/plough, and it
 * reports Create's original breaking pos rather than the destroyed one.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour",
		remap = false)
public abstract class CreateBlockBreakingMovementMixin {

	@Inject(method = "destroyBlock(Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;Lnet/minecraft/core/BlockPos;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$attendedContraptionBreak(@Coerce Object context, BlockPos pos,
			CallbackInfo ci) {
		CreateAttendedAutomation.onContraptionBlockBroken(context, pos);
	}
}
