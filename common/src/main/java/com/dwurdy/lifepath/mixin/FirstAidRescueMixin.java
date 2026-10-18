package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.firstaid.FirstAidMedical;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rescue XP seam for First Aid (New) — the "medic saves another player"
 * path, complementing the self-treatment hooks. Verified against firstaid
 * 1.3.4 bytecode: every revive funnels through
 * {@code EventHandler.completeRescue(ServerPlayer, InteractionTarget)} —
 * the hold-to-rescue progress ticking in {@code tickRescueProgress} and the
 * instant {@code attemptImmediateRescue} (bound-key packet) both end there.
 * Self-defibrillation runs a sibling path in
 * {@code attemptSelfDefibrillator(ServerPlayer)}.
 *
 * <p>Both methods message the acting player only inside the {@code
 * rescueFromCriticalState}-family success branch, so the rescuer's own
 * {@code ServerPlayer.displayClientMessage} invoke is the "rescue actually
 * landed" marker — the victim's message goes through the weaker {@code
 * Player} overload and does not collide with the INVOKE target. The rescue
 * item (bandage stabilise / adrenaline shot / defibrillator) is captured at
 * HEAD because the method consumes it before the success branch.
 */
@Pseudo
@Mixin(targets = "ichttt.mods.firstaid.common.EventHandler", remap = false)
public abstract class FirstAidRescueMixin {

	@Inject(method = "completeRescue(Lnet/minecraft/server/level/ServerPlayer;Lichttt/mods/firstaid/common/EventHandler$InteractionTarget;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private static void lifepath$captureRescueItem(ServerPlayer rescuer,
			@Coerce Object target, CallbackInfo ci) {
		try {
			FirstAidMedical.captureRescueItem(rescuer, target);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"firstaid rescue capture failed", t);
		}
	}

	@Inject(method = "completeRescue(Lnet/minecraft/server/level/ServerPlayer;Lichttt/mods/firstaid/common/EventHandler$InteractionTarget;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"),
			remap = false, require = 0)
	private static void lifepath$onRescue(ServerPlayer rescuer,
			@Coerce Object target, CallbackInfo ci) {
		try {
			FirstAidMedical.onRescueSuccess(rescuer);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"firstaid rescue seam failed", t);
		}
	}

	@Inject(method = "completeRescue(Lnet/minecraft/server/level/ServerPlayer;Lichttt/mods/firstaid/common/EventHandler$InteractionTarget;)V",
			at = @At("RETURN"), remap = false, require = 0)
	private static void lifepath$clearRescue(ServerPlayer rescuer,
			@Coerce Object target, CallbackInfo ci) {
		FirstAidMedical.clearRescue();
	}

	@Inject(method = "attemptSelfDefibrillator(Lnet/minecraft/server/level/ServerPlayer;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerPlayer;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"),
			remap = false, require = 0)
	private static void lifepath$onSelfDefib(ServerPlayer player, CallbackInfo ci) {
		try {
			FirstAidMedical.onSelfDefib(player);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"firstaid self-defib seam failed", t);
		}
	}
}
