package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.relation.DispositionService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M5-4 mob-disposition seam: {@code TargetPredicate.test(owner, target)} is
 * the single predicate every combat-targeting goal consults
 * (ActiveTargetGoal, RevengeGoal, …). When the prospective target is a player
 * whose species declares this mob's type NEUTRAL/FRIENDLY, the test fails —
 * the mob simply never selects them. One injection, no per-mob code.
 */
@Mixin(TargetPredicate.class)
public abstract class TargetPredicateMixin {

	@Inject(method = "test(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/LivingEntity;)Z",
			at = @At("HEAD"), cancellable = true)
	private void lifepath$dispositionGate(LivingEntity mob, LivingEntity target,
			CallbackInfoReturnable<Boolean> cir) {
		if (mob != null && target instanceof ServerPlayerEntity player
				&& DispositionService.blocksTargeting(
						CharacterManager.getCharacter(player), mob.getType())) {
			cir.setReturnValue(false);
		}
	}
}
