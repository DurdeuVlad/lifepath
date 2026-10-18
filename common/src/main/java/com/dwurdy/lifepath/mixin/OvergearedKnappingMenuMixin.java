package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.overgeared.OvergearedStationSupport;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rock-knapping material gate (anti one-man-army): today's knapping recipes
 * all produce ungated stone parts, but the gate check is datapack-driven —
 * any pack/shipped recipe that knaps a gated-tier part (e.g. a metal blade
 * addon) would otherwise bypass the level requirement. Injected after
 * {@code updateResult} writes the result container; a denied stack is
 * cleared before take-out. {@code player}/{@code resultContainer} fields are
 * private → reflective reads in {@link OvergearedStationSupport}.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.screen.RockKnappingMenu",
		remap = false)
public abstract class OvergearedKnappingMenuMixin {

	@Inject(method = "updateResult", at = @At("TAIL"), remap = false,
			require = 0)
	private void lifepath$gateKnappingResult(CallbackInfo ci) {
		try {
			OvergearedStationSupport.gateMenuResult(
					(AbstractContainerMenu) (Object) this, "resultContainer");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("overgeared knapping gate failed", t);
		}
	}
}
