package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kaleidoscope Tavern shaker pickups → alcohol XP (NeoForge-only, gated by the
 * {@code lifepath.compat.kaleidoscopetavern.mixins.json} config's
 * {@code requiredMods}). Verified against kaleidoscopetavern 1.2.0 bytecode:
 * the take-out is a pickup — {@code ShakerBlock.useItemOn} on an empty
 * main-hand drops the shaker block item (contents included) into the taker's
 * inventory and sets the block to air; the crafted cocktail lives in
 * {@code ShakerBlockEntity.getResult()}. Snapshot the result at HEAD only for
 * the pickup branch (main hand + empty held stack + server side), emit on a
 * {@code SUCCESS} return — ingredient inserts and client replays can't trip it.
 * Attributed to the taker.
 */
@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopetavern.block.mixology.ShakerBlock",
		remap = false)
public abstract class KaleidoscopeTavernShakerMixin {

	@Unique
	private ItemStack lifepath$pendingResult;

	@Inject(method = "useItemOn", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$captureResult(ItemStack stack, BlockState state, Level level,
			BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult,
			CallbackInfoReturnable<ItemInteractionResult> cir) {
		lifepath$pendingResult = null;
		if (hand != InteractionHand.MAIN_HAND || !stack.isEmpty() || level.isClientSide()) {
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be != null) {
			lifepath$pendingResult = KaleidoscopeTakeSupport.readStack(be, "getResult");
		}
	}

	@Inject(method = "useItemOn", at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$emit(ItemStack stack, BlockState state, Level level,
			BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult,
			CallbackInfoReturnable<ItemInteractionResult> cir) {
		if (lifepath$pendingResult != null
				&& cir.getReturnValue() == ItemInteractionResult.SUCCESS) {
			KaleidoscopeTakeSupport.emitTake(player, lifepath$pendingResult);
		}
		lifepath$pendingResult = null;
	}
}
