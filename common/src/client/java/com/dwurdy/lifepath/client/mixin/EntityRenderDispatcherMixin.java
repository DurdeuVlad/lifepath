package com.dwurdy.lifepath.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dwurdy.lifepath.client.morph.MorphDisguiseClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M-4 disguise seam: {@code EntityRenderDispatcher.render} is the single
 * funnel every entity render goes through — world entities (local F5 and
 * remote players), GUI previews, the works. A morphed player is swapped
 * for the cached animal entity; everything else (players un-morphed, the
 * animal prop itself — recursion is impossible, it's not a Player) passes
 * through untouched.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void lifepath$morphDisguise(E entity, double x,
			double y, double z, float yaw, float tickDelta, PoseStack matrices,
			MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (entity instanceof Player player
				&& MorphDisguiseClient.render(player,
						(EntityRenderDispatcher) (Object) this,
						x, y, z, yaw, tickDelta, matrices, buffers, light)) {
			ci.cancel();
		}
	}
}
