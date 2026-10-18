package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.zetter.ZetterArtistry;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scholarship seam for Zetter's signature act — signing a canvas on the
 * easel, the moment a painted canvas becomes a {@code zetter:painting} item.
 * {@code ServerHandler.processSignPainting} validates slot + canvas +
 * canvas-data and only then reaches the {@code Inventory.setItem} line that
 * installs the produced painting; injecting there pays exactly on committed
 * production — rejected packets and per-stroke spam ({@code processAction},
 * deliberately not hooked) never reach it. Static target → static handler;
 * the packet param is {@link Coerce}d.
 */
@Pseudo
@Mixin(targets = "me.dantaeusb.zetter.network.ServerHandler", remap = false)
public abstract class ZetterSignPaintingMixin {

	@Inject(method = "processSignPainting(Lme/dantaeusb/zetter/network/packet/CSignPaintingPacket;Lnet/minecraft/server/level/ServerPlayer;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"),
			remap = false, require = 0)
	private static void lifepath$onPaintingSigned(@Coerce Object packet,
			ServerPlayer player, CallbackInfo ci) {
		try {
			ZetterArtistry.emitPaintingSigned(player);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("zetter sign-painting seam failed", t);
		}
	}
}
