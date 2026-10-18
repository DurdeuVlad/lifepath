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
 * Scholarship seam for Esquisse's sketchbook drawing —
 * {@code SketchbookPayloadHandler.handleData} only {@code enqueueWork}s, so
 * the hook sits at the head of the server-thread work lambda where the mod
 * itself validates (sketchbook in hand, page index in range) and spends
 * pencil/eraser pixel counts from the payload. Both params are {@link Coerce}d
 * — the payload is esquisse's record, the context a NeoForge type; the helper
 * re-derives the guards reflectively and pays only for saves that actually
 * laid pixels. Static target method → static handler.
 */
@Pseudo
@Mixin(targets = "net.avizvul.esquissemod.network.SketchbookPayloadHandler",
		remap = false)
public abstract class EsquisseSketchSaveMixin {

	@Inject(method = "lambda$handleData$0(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/avizvul/esquissemod/network/SketchbookSavePayload;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private static void lifepath$onSketchSave(@Coerce Object ctx,
			@Coerce Object payload, CallbackInfo ci) {
		try {
			EsquisseSketching.emitSketchSave(ctx, payload);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("esquisse sketch-save seam failed", t);
		}
	}
}
