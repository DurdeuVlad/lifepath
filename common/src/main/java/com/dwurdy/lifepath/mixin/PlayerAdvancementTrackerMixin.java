package com.dwurdy.lifepath.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
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
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementTrackerMixin {
	@Shadow
	@Final
	private ServerPlayer player;

	@Shadow
	public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

	@Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("RETURN"))
	private void lifepath$unlockOnAdvancement(AdvancementHolder advancement,
			String criterionName, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue()) && getOrStartProgress(advancement).isDone()) {
			com.dwurdy.lifepath.unlock.UnlockService
					.onAdvancement(player, advancement.id(), System.currentTimeMillis());
		}
	}
}
