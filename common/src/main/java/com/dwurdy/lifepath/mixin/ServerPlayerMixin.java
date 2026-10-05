package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.condition.ConditionService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M17 death seam: {@code type:death} condition acquisition fires once the
 * vanilla death path completes — character data persists across respawn,
 * so a {@code phoenix_rebirth}-style condition survives the transition.
 * Must live on {@link ServerPlayer}: it fully reimplements {@code die}
 * (combat tracker, death message, loot) without delegating to
 * {@code Player.die} or {@code LivingEntity.die}, so an inject on either
 * superclass never runs for players.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

	@Inject(method = "die", at = @At("TAIL"))
	private void lifepath$deathAcquisition(DamageSource source, CallbackInfo ci) {
		ConditionService.onDeath((ServerPlayer) (Object) this,
				System.currentTimeMillis());
	}
}
