package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.skill.SkillGates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M29 skill gates — entity-interact refusal. {@code interactOn} covers
 * villager trading, breeding, saddling, minecart chests; FAIL
 * short-circuits before the entity's own interact logic. The held stack's
 * {@code use} gate is checked first — item-on-entity paths (lead on an
 * animal, bandage on a player) never pass through {@code useItem}, and
 * unlike the block seam they can't be split: {@code entity.interact} IS
 * the funnel that applies the item (leash, feed, saddle), so the gate
 * must refuse the whole touch. Deliberate consequence: a gated item in
 * hand refuses every entity interaction — switch to a free hand to trade.
 * Server-side only ({@link ServerPlayer}) — the client falls through to
 * prediction.
 */
@Mixin(Player.class)
public abstract class PlayerInteractGateMixin {
	@Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
	private void lifepath$gateInteractOn(Entity entity, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (!((Object) this instanceof ServerPlayer player)) {
			return;
		}
		SkillGates.Denial denial = SkillGates.denialForUse(player,
				player.getItemInHand(hand));
		if (denial == null) {
			denial = SkillGates.denialForInteract(player, entity);
		}
		if (denial != null) {
			SkillGates.notifyDenied(player, denial);
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
