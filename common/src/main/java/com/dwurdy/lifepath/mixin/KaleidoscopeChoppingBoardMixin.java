package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kaleidoscope Cookery chopping-board takes → Cooking XP (NeoForge-only, gated
 * by the {@code lifepath.compat.kaleidoscope.mixins.json} config's
 * {@code requiredMods}). Verified against KC 1.5.1 bytecode:
 * {@code ChoppingBoardBlockEntity.onTakeOut(Level, LivingEntity)} hands over
 * the cut result, exposed beforehand via {@code getCurrentCutStack()}.
 * Attributed to the taker.
 */
@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity",
		remap = false)
public abstract class KaleidoscopeChoppingBoardMixin {

	@Unique
	private ItemStack lifepath$pendingResult = ItemStack.EMPTY;

	@Inject(method = "onTakeOut", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$captureResult(Level level, LivingEntity taker,
			CallbackInfoReturnable<Boolean> cir) {
		lifepath$pendingResult =
				KaleidoscopeTakeSupport.readStack(this, "getCurrentCutStack");
	}

	@Inject(method = "onTakeOut", at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$emit(Level level, LivingEntity taker,
			CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			KaleidoscopeTakeSupport.emitTake(taker, lifepath$pendingResult);
		}
		lifepath$pendingResult = ItemStack.EMPTY;
	}
}
