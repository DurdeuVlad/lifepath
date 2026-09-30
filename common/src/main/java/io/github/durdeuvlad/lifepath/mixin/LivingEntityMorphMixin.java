package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.morph.MorphDisguise;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M-4 morph hitbox: {@code LivingEntity.getDimensions(Pose)} is final and
 * funnels through {@code getDefaultDimensions} — the override seam. A
 * morphed player reports the form's {@link EntityDimensions}, which also
 * carries the animal's eye height (so the camera lands at fox height, not
 * 1.62m). Both sides run this: the server for the real collision box,
 * the client for prediction/render consistency.
 *
 * <p>Scale composes naturally — the outer {@code getDimensions} applies
 * {@code getScale()} to whatever this returns.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMorphMixin {
	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphDimensions(Pose pose,
			CallbackInfoReturnable<EntityDimensions> cir) {
		EntityDimensions dims = MorphDisguise.dimsFor((Entity) (Object) this);
		if (dims != null) {
			cir.setReturnValue(dims);
		}
	}
}
