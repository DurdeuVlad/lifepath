package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.bioforge.BioForgeDiagnosis;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnosis XP seam for BioForge: the microscope's identify button lands in
 * {@code MicroscopeBlockEntity.handleButton(Player, int)} after the player
 * completes the calibration sliders — one emit per identification, gated on
 * the identify-button id (resolved reflectively in the helper) and a true
 * return. Params are vanilla types; the target stays a string + {@link Pseudo}.
 */
@Pseudo
@Mixin(targets = "net.jenkimods.bioforge.world.microscope.MicroscopeBlockEntity",
		remap = false)
public abstract class BioForgeMicroscopeMixin {

	@Inject(method = "handleButton(Lnet/minecraft/world/entity/player/Player;I)Z",
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$onIdentify(Player player, int buttonId,
			CallbackInfoReturnable<Boolean> cir) {
		try {
			if (Boolean.TRUE.equals(cir.getReturnValue())) {
				BioForgeDiagnosis.onButton(player, this, buttonId);
			}
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"bioforge microscope seam failed", t);
		}
	}
}
