package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.icysbetterhorses.BetterHorsesSupport;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Icy's Better Horses roster-management seam (LP-INT-2), gated by
 * {@code lifepath.compat.icys_better_horses.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code HorseManagement.whistle / sendHome / setActive} are the
 * server-side funnels behind the stable-management UI (packet/menu driven).
 * {@code disown} is excluded — relinquishing a horse is not a husbandry act.
 * Each takes {@code (ServerPlayer, horseUuid)} and returns the
 * {@code Outcome} record — a resolved ({@code ok()}) command is an attended
 * rancher act on the player's own horse → {@code lifepath:cared} on the
 * generic {@code minecraft:horse} subject (the managed horse may live
 * unloaded in storage, so there is no entity to read tags from).
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.HorseManagement", remap = false)
public abstract class BetterHorsesManagementMixin {

	@Inject(method = {"whistle", "sendHome", "setActive"},
			at = @At("RETURN"), require = 0)
	private static void lifepath$bhManaged(ServerPlayer player, UUID horse,
			CallbackInfoReturnable<?> cir) {
		if (BetterHorsesSupport.outcomeOk(cir.getReturnValue())) {
			BetterHorsesSupport.emitHorseManaged(player);
		}
	}
}
