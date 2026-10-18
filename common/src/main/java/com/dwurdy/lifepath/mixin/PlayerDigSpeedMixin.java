package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.skill.SkillGates;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M29 skill gates — dig-speed degrade. Scales the resolved destroy speed
 * by {@link SkillGates#breakSpeedFactor} (0.35 when the held tool's skill
 * gate denies the player, 1.0 otherwise). Applied at RETURN so it runs on
 * both sides — the client predicts the same slowed progress the server
 * enforces, keeping the crack animation honest.
 */
@Mixin(Player.class)
public abstract class PlayerDigSpeedMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void lifepath$gateDigSpeed(BlockState state,
			CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(cir.getReturnValueF()
				* SkillGates.breakSpeedFactor((Player) (Object) this, state));
	}
}
