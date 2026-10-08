package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M24 placed-block guard, loader-order-proof: snapshots
 * {@code PlacedBlockTracker.contains(pos)} at {@code destroyBlock} HEAD —
 * before NeoForge's {@code BreakEvent} (pre-break) can consume the mark and
 * before {@code getDrops} runs inside {@code playerDestroy} on either loader.
 * The drop seam reads the snapshot instead of re-asking the tracker.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class GameModeOutcomeMixin {

	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "destroyBlock", at = @At("HEAD"))
	private void lifepath$capturePlacedFlag(BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		VanillaGameplayProducers.onDestroyBlockStart(player, pos);
	}
}
