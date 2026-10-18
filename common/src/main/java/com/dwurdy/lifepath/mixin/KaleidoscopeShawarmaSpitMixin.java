package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
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
 * Kaleidoscope Cookery shawarma-spit carves → Cooking XP (NeoForge-only, gated
 * by the {@code lifepath.compat.kaleidoscope.mixins.json} config's
 * {@code requiredMods}). Verified against KC 1.6.0 bytecode (class is
 * md5-identical on 1.5.1): {@code ShawarmaSpitBlockEntity.onTakeCookedItem(
 * Level, LivingEntity)} has no {@code getOutputs()} — the take hands a copy of
 * the public {@code cookedItem} field to the taker via private
 * {@code giveItem(...)} once {@code cookTime} reaches zero. Carving early
 * ({@code cookTime > 0}) instead returns the raw {@code cookingItem} — an
 * ingredient retrieval, not a craft output, so it earns nothing. Attributed to
 * the taker.
 */
@Pseudo
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ShawarmaSpitBlockEntity",
		remap = false)
public abstract class KaleidoscopeShawarmaSpitMixin {

	@Unique
	private ItemStack lifepath$pendingResult = ItemStack.EMPTY;

	@Inject(method = "onTakeCookedItem", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$captureResult(Level level, LivingEntity taker,
			CallbackInfoReturnable<Boolean> cir) {
		lifepath$pendingResult = lifepath$takePreview();
	}

	@Inject(method = "onTakeCookedItem", at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$emit(Level level, LivingEntity taker,
			CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			KaleidoscopeTakeSupport.emitTake(taker, lifepath$pendingResult);
		}
		lifepath$pendingResult = ItemStack.EMPTY;
	}

	/**
	 * The stack {@code onTakeCookedItem} will hand over: the finished dish when
	 * {@code cookTime <= 0}, {@link ItemStack#EMPTY} while still cooking (the
	 * early-carve branch gives back the raw {@code cookingItem}, which is not a
	 * produced output). Both are public fields on the block entity — populated
	 * together when the item is loaded — read reflectively so a KC refactor
	 * degrades to a debug log like the other accessors.
	 */
	@Unique
	private ItemStack lifepath$takePreview() {
		try {
			Class<?> cls = getClass();
			if (cls.getField("cookTime").getInt(this) <= 0) {
				Object cooked = cls.getField("cookedItem").get(this);
				if (cooked instanceof ItemStack stack) {
					return stack.copy();
				}
			}
		} catch (ReflectiveOperationException | ClassCastException e) {
			LifepathMod.LOGGER.debug("KC take support: ShawarmaSpitBlockEntity "
					+ "field read failed: {}", e.toString());
		}
		return ItemStack.EMPTY;
	}
}
