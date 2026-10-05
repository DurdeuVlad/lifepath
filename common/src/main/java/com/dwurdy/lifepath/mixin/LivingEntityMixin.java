package com.dwurdy.lifepath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.dwurdy.lifepath.ability.AbilityEngine;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M5-2 seam for {@code damage_taken} abilities: scales the damage amount at the
 * {@code actuallyHurt} call inside {@link LivingEntity#hurt} — after
 * invulnerability/shield gating, before armor/enchantment/resistance reduce it.
 * Subclasses that route through {@code super.hurt} (Player) are covered
 * because the interception is on the call site. Generic: conditions inspect
 * attacker/source/amount via {@code EvalContext.damage()}; no species-specific
 * logic lives here.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@WrapOperation(method = "hurt", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"))
	private void lifepath$scaleIncomingDamage(LivingEntity self, DamageSource source, float amount,
			Operation<Void> original) {
		original.call(self, source, AbilityEngine.modifyIncomingDamage(self, source, amount));
	}

	/**
	 * Full negation must behave like vanilla's {@code FIRE_RESISTANCE} early
	 * exit, not like a zero-damage hit: scaling to zero inside
	 * {@code actuallyHurt} leaves {@code hurt} broadcasting the hurt event —
	 * red flash, hurt sound, knockback — which reads as taking damage (Beta 7:
	 * "fire hurts the dragonborn while it heals"). Returning false at the head
	 * skips all of it. Conditions are pure, so the head eval and the wrap eval
	 * can both run on a partially-reduced hit without side effects.
	 */
	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void lifepath$negatedHitSkipsFeedback(DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		if (AbilityEngine.isFullyNegated((LivingEntity) (Object) this, source, amount)) {
			cir.setReturnValue(false);
		}
	}
}
