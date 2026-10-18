package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.esquisse.EsquisseSketching;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scholarship seam for Esquisse's page tear-out — the production moment for
 * the {@code esquissemod:sketched_page} item the player can frame, place or
 * share. Hooked at the head of the {@code enqueueWork} lambda (same shape as
 * the save seam); the helper reads the sketchbook's page component
 * pre-mutation and pays only when the torn page actually holds a sketch —
 * blank-page tears produce {@code empty_page} and earn nothing.
 */
@Pseudo
@Mixin(targets = "net.avizvul.esquissemod.network.SketchbookPayloadHandler",
		remap = false)
public abstract class EsquisseTearPageMixin {

	@Inject(method = "lambda$handleTearPage$1(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/avizvul/esquissemod/network/TearPagePayload;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private static void lifepath$onTearPage(@Coerce Object ctx,
			@Coerce Object payload, CallbackInfo ci) {
		try {
			EsquisseSketching.emitTearPage(ctx, payload);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("esquisse tear-page seam failed", t);
		}
	}
}
