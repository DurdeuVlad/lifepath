package io.github.durdeuvlad.lifepath.mixin;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Quest/narrative unlock hook (M9-4): when a criterion grant completes an
 * advancement, {@code type:advancement} unlock sources evaluate against the
 * advancement id. Fires once per completion — {@code grantCriterion} only
 * returns true on actual progress, and {@code isDone} flips exactly once.
 */
@Mixin(PlayerAdvancementTracker.class)
public abstract class PlayerAdvancementTrackerMixin {
	@Shadow
	@Final
	private ServerPlayerEntity owner;

	@Shadow
	public abstract AdvancementProgress getProgress(AdvancementEntry advancement);

	@Inject(method = "grantCriterion", at = @At("RETURN"))
	private void lifepath$unlockOnAdvancement(AdvancementEntry advancement,
			String criterionName, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue()) && getProgress(advancement).isDone()) {
			io.github.durdeuvlad.lifepath.unlock.UnlockService
					.onAdvancement(owner, advancement.id(), System.currentTimeMillis());
		}
	}
}
