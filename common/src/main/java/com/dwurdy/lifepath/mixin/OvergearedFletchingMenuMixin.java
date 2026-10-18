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
 * Fletching-station material gate (anti one-man-army): the station's result
 * slot can hold tier-gated arrows ({@code overgeared:iron_arrow} → gate 10,
 * {@code steel_arrow}/{@code diamond_arrow} → 30/50), so the take-out seam
 * alone would be a bypass the vanilla smithing table no longer offers.
 * Injected after {@code updateResultSlot} computes the result — a denied
 * stack is cleared before it can be taken, mirroring
 * {@code SmithingScreenHandlerMixin#createResult}. The menu's {@code player}
 * and {@code result} fields are private → read reflectively in
 * {@link OvergearedStationSupport}; an Overgeared refactor degrades to a
 * debug log.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.screen.FletchingStationMenu",
		remap = false)
public abstract class OvergearedFletchingMenuMixin {

	@Inject(method = "updateResultSlot", at = @At("TAIL"), remap = false,
			require = 0)
	private void lifepath$gateFletchingResult(CallbackInfo ci) {
		try {
			OvergearedStationSupport.gateMenuResult(
					(AbstractContainerMenu) (Object) this, "result");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("overgeared fletching gate failed", t);
		}
	}
}
