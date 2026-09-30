package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.morph.MorphDisguise;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M-4 morph hitbox: {@code LivingEntity.getDimensions(Pose)} is final and
 * funnels through {@code getDefaultDimensions} — the override seam.
 * {@link Player} overrides it to serve the {@code POSES} map (never
 * delegating to {@code LivingEntity}), so the seam must live on Player
 * itself: injecting LivingEntity would never run for the only entity type
 * that can morph. A morphed player reports the form's
 * {@link EntityDimensions}, which also carries the animal's eye height (so
 * the camera lands at fox height, not 1.62m). Both sides run this: the
 * server for the real collision box, the client for prediction/render
 * consistency.
 *
 * <p>Scale composes naturally — the outer {@code getDimensions} applies
 * {@code getScale()} to whatever this returns. Pose variants intentionally
 * collapse to the form's base dims: the animal's own sleeping/swimming
 * profile is datapack territory, not per-pose player mapping.
 */
@Mixin(Player.class)
public abstract class PlayerMorphDimensionsMixin {
	@Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphDimensions(Pose pose,
			CallbackInfoReturnable<EntityDimensions> cir) {
		EntityDimensions dims = MorphDisguise.dimsFor((Entity) (Object) this);
		if (dims != null) {
			cir.setReturnValue(dims);
		}
	}
}
