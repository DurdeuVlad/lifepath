package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.icysbetterhorses.BetterHorsesSupport;
import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Icy's Better Horses cart-interaction seam (LP-INT-2), gated by
 * {@code lifepath.compat.icys_better_horses.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code HorseCartEntity.interactAt} is the player funnel for the
 * horse-cart entity: attaching/removing chests and ploughs, opening the
 * cart chest, boarding. These are acts on the hitched animal's rig →
 * {@code lifepath:cared} on the bound horse ({@code boundHorse()} — the
 * cart itself is an {@code Entity}, not a LivingEntity/husbandry subject).
 * Skips silently while the cart is unbound. The cart's automated tilling
 * ({@code tillGround}) is intentionally not paid — vanilla pays nothing
 * for hoeing either.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.entity.HorseCartEntity", remap = false)
public abstract class BetterHorsesCartMixin {

	@Inject(method = "interactAt", at = @At("TAIL"), require = 0)
	private void lifepath$bhCartTended(Player player, Vec3 hit,
			InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()) {
			return;
		}
		LivingEntity horse = BetterHorsesSupport.boundHorse(this);
		if (horse != null) {
			HusbandryProducers.onMobCared(player, horse);
		}
	}
}
