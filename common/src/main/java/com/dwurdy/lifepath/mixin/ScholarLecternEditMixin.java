package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.scholar.ScholarScribing;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scholarship seam for Scholar's signature feature — editing and signing a
 * book that sits on a lectern. {@code LecternEditBookC2SP.handle} is the
 * server funnel for both: it verifies the lectern block entity exists, then
 * queues the page write / sign through the async text filter. A {@code true}
 * return means the work was accepted; {@code this} is the packet record, so
 * the helper reads {@code title()} to tell a sign ({@code written_book}) from
 * an edit ({@code writable_book}). Pays on acceptance, matching the firstaid
 * receipt convention — a profanity-rejected page is a datapack-rate question,
 * not a seam risk.
 */
@Pseudo
@Mixin(targets = "io.github.mortuusars.scholar.network.packet.serverbound.LecternEditBookC2SP",
		remap = false)
public abstract class ScholarLecternEditMixin {

	@Inject(method = "handle(Lnet/minecraft/network/protocol/PacketFlow;Lnet/minecraft/world/entity/player/Player;)Z",
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$onLecternEdit(PacketFlow flow, Player player,
			CallbackInfoReturnable<Boolean> cir) {
		try {
			ScholarScribing.emitLecternEdit(this, player, cir.getReturnValueZ());
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("scholar lectern-edit seam failed", t);
		}
	}
}
