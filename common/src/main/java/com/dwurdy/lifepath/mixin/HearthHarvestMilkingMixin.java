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
 * Hearth &amp; Harvest bottle-milking seam (LP-INT-2), gated by
 * {@code lifepath.compat.hearthandharvest.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>HH's {@code CowMilking}/{@code GoatMilking} NeoForge handlers milk
 * cows/goats with a glass bottle directly inside
 * {@code EntityInteract} — they {@code setCanceled(true)} with a SUCCESS
 * cancellation result, so the vanilla {@code mobInteract} path (and our
 * {@code Cow}/{@code Goat} {@code createFilledResult} seam) never runs.
 * Emitting {@code lifepath:milked} here restores parity with bucket
 * milking — same animal, same take-out, same player act.
 *
 * <p>Emits only on the cancelled-and-consumed branch: the FAIL-cancel and
 * config-disabled paths produce nothing.
 */
@Pseudo
@Mixin(targets = {
		"alabaster.hearthandharvest.common.event.CowMilking",
		"alabaster.hearthandharvest.common.event.GoatMilking"},
		remap = false)
public abstract class HearthHarvestMilkingMixin {

	@Inject(method = "onRightClickEntity", at = @At("RETURN"), require = 0)
	private static void lifepath$hhBottleMilked(@Coerce Object event, CallbackInfo ci) {
		if (!HearthHarvestSupport.cancelledAndConsumed(event)) {
			return;
		}
		Player player = HearthHarvestSupport.eventPlayer(event);
		LivingEntity target = HearthHarvestSupport.eventTarget(event);
		if (player != null && target instanceof Animal animal) {
			HusbandryProducers.onMilkTaken(player, animal);
		}
	}
}
