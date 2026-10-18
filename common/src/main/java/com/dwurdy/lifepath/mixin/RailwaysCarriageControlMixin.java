package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.railways.RailwaysPossessedOperator;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Steam 'n' Rails possessed-conductor seam (NeoForge-only, gated by the
 * {@code lifepath.compat.railways.mixins.json} config's {@code requiredMods}).
 * Verified against railways 0.2.1 bytecode: {@code
 * ConductorEntity$RemoteControlGoal} forwards every drive input through
 * {@code CarriageContraptionEntity.control(pos, controls, fakePlayer)} — the
 * shared {@code [Conductor]} FakePlayer, never a real player. HEAD of that
 * method is the one point that sees both the carriage and the proxy caller,
 * letting {@link RailwaysPossessedOperator} record who truly drives when the
 * conductor is possessed — and clear the record for every other caller. The
 * control itself is never altered (observe-only; {@code require = 0}).
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.trains.entity.CarriageContraptionEntity",
		remap = false)
public abstract class RailwaysCarriageControlMixin {

	@Inject(method = "control(Lnet/minecraft/core/BlockPos;Ljava/util/Collection;Lnet/minecraft/world/entity/player/Player;)Z",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$possessedConductorControl(BlockPos pos, Collection<?> inputs,
			Player player, CallbackInfoReturnable<Boolean> cir) {
		RailwaysPossessedOperator.onCarriageControl(this, player);
	}
}
