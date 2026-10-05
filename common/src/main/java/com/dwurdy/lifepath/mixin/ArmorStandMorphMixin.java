package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.morph.MorphDisguise;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M-5 complement to the {@code Player.interactOn} gate: the INTERACT_AT
 * packet path dispatches {@code Entity.interactAt} directly
 * ({@code ServerGamePacketListenerImpl$1.onInteraction(hand, vec)}),
 * bypassing {@code Player.interactOn}. The base {@code Entity.interactAt}
 * is a trivial PASS, so a mixin there can never catch anything — the deny
 * must live on the override. {@link ArmorStand} is vanilla's only
 * {@code interactAt} implementation (precise-point equipment swaps), so
 * it is gated here. PASS makes the click behave as if it never landed:
 * no fallback runs server-side, and the client folds back into the
 * separately-gated {@code interactOn} path.
 *
 * <p>Residual: a modded entity with its own {@code interactAt} override
 * would bypass this — the complete seam is the packet handler's anonymous
 * dispatch class, rejected for fragility.
 */
@Mixin(ArmorStand.class)
public abstract class ArmorStandMorphMixin {
	@Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphDenyInteractAt(Player player, Vec3 vec,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (MorphDisguise.isDisguised(player)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
