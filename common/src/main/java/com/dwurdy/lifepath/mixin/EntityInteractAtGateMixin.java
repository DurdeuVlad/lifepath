package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.skill.SkillGates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M29 skill gates — the {@code INTERACT_AT} seam. The interact packet
 * dispatches position-targeted clicks straight to
 * {@code entity.interactAt} — {@code Player.interactOn} never runs, so
 * without this the interact gate has a hole (armor stands, multipart
 * hitboxes, modded NPCs). Checks the interact gate on the target first,
 * then the held stack's use gate so item-on-entity application is
 * refused here too. Server-side only.
 */
@Mixin(Entity.class)
public abstract class EntityInteractAtGateMixin {
	@Inject(method = "interactAt", at = @At("HEAD"), cancellable = true)
	private void lifepath$gateInteractAt(Player player, Vec3 hitPos,
			InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		SkillGates.Denial denial = SkillGates.denialForInteract(serverPlayer,
				(Entity) (Object) this);
		if (denial == null) {
			denial = SkillGates.denialForUse(serverPlayer,
					serverPlayer.getItemInHand(hand));
		}
		if (denial != null) {
			SkillGates.notifyDenied(serverPlayer, denial);
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
