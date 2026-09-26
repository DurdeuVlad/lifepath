package io.github.durdeuvlad.lifepath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.durdeuvlad.lifepath.ability.AbilityEngine;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M5-2 seam for {@code damage_taken} abilities: scales the damage amount at the
 * {@code applyDamage} call inside {@link LivingEntity#damage} — after
 * invulnerability/shield gating, before armor/enchantment/resistance reduce it.
 * Subclasses that route through {@code super.damage} (PlayerEntity) are covered
 * because the interception is on the call site. Generic: conditions inspect
 * attacker/source/amount via {@code EvalContext.damage()}; no species-specific
 * logic lives here.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@WrapOperation(method = "damage", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/LivingEntity;applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
	private void lifepath$scaleIncomingDamage(LivingEntity self, DamageSource source, float amount,
			Operation<Void> original) {
		original.call(self, source, AbilityEngine.modifyIncomingDamage(self, source, amount));
	}
}
