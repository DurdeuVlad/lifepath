package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.productivefarming.ProductiveFarmingSupport;
import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Productive Farming wolf-armor seam (LP-INT-2), gated by
 * {@code lifepath.compat.productivefarming.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code EventHandler.onPlayerInteract} equips PF's wolf body-armor
 * (and removes it with shears) inside {@code EntityInteract}, cancelling
 * with a consumed result — {@code Animal.mobInteract} and the item-side
 * funnel never run, so the vanilla seams stay blind. Equipping or
 * un-equipping the wolf's armor is animal handling →
 * {@code lifepath:cared}.
 */
@Pseudo
@Mixin(targets = "cy.jdkdigital.productivefarming.event.EventHandler",
		remap = false)
public abstract class ProductiveFarmingWolfArmorMixin {

	@Inject(method = "onPlayerInteract", at = @At("RETURN"), require = 0)
	private static void lifepath$pfWolfTended(@Coerce Object event, CallbackInfo ci) {
		if (!ProductiveFarmingSupport.cancelledAndConsumed(event)) {
			return;
		}
		Player player = ProductiveFarmingSupport.eventPlayer(event);
		LivingEntity target = ProductiveFarmingSupport.eventTarget(event);
		if (player != null && target != null) {
			HusbandryProducers.onMobCared(player, target);
		}
	}
}
