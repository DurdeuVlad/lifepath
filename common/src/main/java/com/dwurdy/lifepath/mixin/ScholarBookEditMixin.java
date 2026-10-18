package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.scholar.ScholarScribing;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scholarship seam for in-hand book writing — Scholar's spread editor (the
 * mod's primary UX) posts the ordinary vanilla
 * {@code ServerboundEditBookPacket}, so the vanilla listener is the funnel
 * both editors share. Emits at HEAD, mirroring vanilla's own first guard
 * (the targeted slot must hold a writable book) before paying. Ships in the
 * scholar-gated config because it exists to cover the mod's UI; promoting it
 * to {@code lifepath.mixins.json} later would pay vanilla book authoring
 * without the mod installed — same seam, same subject ids.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ScholarBookEditMixin {

	@Inject(method = "handleEditBook(Lnet/minecraft/network/protocol/game/ServerboundEditBookPacket;)V",
			at = @At("HEAD"), require = 0)
	private void lifepath$onBookEdit(ServerboundEditBookPacket packet,
			CallbackInfo ci) {
		try {
			ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
			ScholarScribing.emitBookEdit(player, packet);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("scholar in-hand book-edit seam failed", t);
		}
	}
}
