package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.hearthandharvest.HearthHarvestSupport;
import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hearth &amp; Harvest chicken-plucking seam (LP-INT-2), gated by
 * {@code lifepath.compat.hearthandharvest.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code ChickenPlucking.onPlayerInteractEntity} harvests a feather from
 * a live chicken inside {@code EntityInteract} (cooldown-gated via the
 * {@code PLUCK_COOLDOWN} attachment) and cancels with a consumed result —
 * neither {@code ShearsItem.interactLivingEntity} nor
 * {@code Animal.mobInteract} ever runs, so the vanilla seams stay blind.
 * Plucking is the sheared family — live-animal produce removal, no kill —
 * and emits {@code lifepath:sheared} exactly like sheep shearing.
 *
 * <p>The cooldown-rejection branch cancels with a FAIL result →
 * {@link HearthHarvestSupport#cancelledAndConsumed} reads false → no emit.
 */
@Pseudo
@Mixin(targets = "alabaster.hearthandharvest.common.event.ChickenPlucking",
		remap = false)
public abstract class HearthHarvestPluckingMixin {

	@Inject(method = "onPlayerInteractEntity", at = @At("RETURN"), require = 0)
	private static void lifepath$hhPlucked(@Coerce Object event, CallbackInfo ci) {
		if (!HearthHarvestSupport.cancelledAndConsumed(event)) {
			return;
		}
		Player player = HearthHarvestSupport.eventPlayer(event);
		LivingEntity target = HearthHarvestSupport.eventTarget(event);
		if (player != null && target instanceof Animal animal) {
			HusbandryProducers.onSheared(player, animal);
		}
	}
}
