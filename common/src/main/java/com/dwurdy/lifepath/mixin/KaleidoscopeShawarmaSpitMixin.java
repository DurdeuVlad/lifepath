package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import java.util.List;
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
 * Kaleidoscope Cookery shawarma-spit carves → Cooking XP (NeoForge-only, gated
 * by the {@code lifepath.compat.kaleidoscope.mixins.json} config's
 * {@code requiredMods}). Verified against KC 1.5.1 bytecode:
 * {@code ShawarmaSpitBlockEntity.onTakeCookedItem(Level, LivingEntity)} removes
 * a carved portion from the {@code getOutputs()} item handler; the removed
 * delta is the dish. Attributed to the taker.
 */
@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ShawarmaSpitBlockEntity",
		remap = false)
public abstract class KaleidoscopeShawarmaSpitMixin {

	@Unique
	private List<ItemStack> lifepath$before = List.of();

	@Inject(method = "onTakeCookedItem", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$capture(Level level, LivingEntity taker,
			CallbackInfoReturnable<Boolean> cir) {
		lifepath$before = KaleidoscopeTakeSupport.readStacks(this, "getOutputs");
	}

	@Inject(method = "onTakeCookedItem", at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$emit(Level level, LivingEntity taker,
			CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			KaleidoscopeTakeSupport.emitDelta(taker, lifepath$before,
					KaleidoscopeTakeSupport.readStacks(this, "getOutputs"));
		}
		lifepath$before = List.of();
	}
}
