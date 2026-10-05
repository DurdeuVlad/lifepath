package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.morph.MorphDisguise;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M-5 "paws, not hands" — the player-side gates that must run on BOTH
 * sides. {@code getDestroySpeed} → 0 while morphed makes mining impossible
 * with zero progress (no cracking animation, no break-through; the client
 * predicts the same zero the server enforces). {@code interactOn} gates
 * entity interaction (villager trading, breeding, minecart chests) —
 * returning PASS short-circuits before the entity's own interact logic.
 * Melee bite stays untouched: {@code attack} is a different method.
 */
@Mixin(Player.class)
public abstract class PlayerMorphHandsMixin {
	@Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoDig(BlockState state,
			CallbackInfoReturnable<Float> cir) {
		if (MorphDisguise.isDisguised((Entity) (Object) this)) {
			cir.setReturnValue(0.0f);
		}
	}

	@Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoInteract(Entity entity, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (MorphDisguise.isDisguised((Entity) (Object) this)) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
