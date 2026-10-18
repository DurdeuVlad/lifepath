package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.firstaid.FirstAidMedical;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Treatment XP seam for First Aid (New)'s body-part apply GUI:
 * {@code MessageApplyHealingItem$Handler.onMessage} is the server entry for
 * "apply this medicine to that body part". Both params are {@link Coerce}d —
 * the packet type is firstaid's and {@code IPayloadContext} is a neoforge
 * type unavailable in the common module; the emit helper reads player, part
 * and hand reflectively. Pays on packet receipt (the mod validates the apply
 * itself — a rejected apply is a datapack-rate question, not a seam risk).
 */
@Pseudo
@Mixin(targets = "ichttt.mods.firstaid.common.network.MessageApplyHealingItem$Handler",
		remap = false)
public abstract class FirstAidApplyHealingMixin {

	@Inject(method = "onMessage(Lichttt/mods/firstaid/common/network/MessageApplyHealingItem;Lnet/neoforged/neoforge/network/handling/IPayloadContext;)V",
			at = @At("RETURN"), remap = false, require = 0)
	private static void lifepath$onHealingApply(@Coerce Object msg, @Coerce Object ctx,
			CallbackInfo ci) {
		try {
			FirstAidMedical.emitHealingApply(msg, ctx);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"firstaid body-part apply seam failed", t);
		}
	}
}
