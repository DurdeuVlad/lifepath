package com.dwurdy.lifepath.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Timber Tales 1.1.3 cancels {@code BlockEvent.BreakEvent} while its
 * cut-progress is incomplete and removes the felled column asynchronously via
 * {@code FallingTreeManager.startOrQueue} ({@code ServerLevel.setBlock} +
 * {@code dropResources}) — no break events, so the vanilla break path pays
 * nothing on TT trees. The single {@code startOrQueue} call inside
 * {@code TimberTalesEvents.onBreakBlock} is the only player-attributed point
 * in the fell path (the {@code BreakEvent} still carries player + state), so
 * we publish one mining activity per queued fell there — the act the player
 * actually performed. NeoForge-only, gated by
 * {@code lifepath.compat.timbertales.mixins.json}'s {@code requiredMods}.
 */
@Pseudo
@Mixin(targets = "com.pichau.timbertales.TimberTalesEvents", remap = false)
public abstract class TimberTalesEventsMixin {

	@Inject(method = "onBreakBlock(Lnet/neoforged/neoforge/event/level/BlockEvent$BreakEvent;)V",
			remap = false, require = 0,
			at = @At(value = "INVOKE",
					target = "Lcom/pichau/timbertales/tree/FallingTreeManager;startOrQueue(Lnet/minecraft/server/level/ServerLevel;Lcom/pichau/timbertales/tree/TreeScan;Lnet/minecraft/core/Direction;Ljava/util/Map;)V",
					remap = false))
	private void lifepath$treeFelled(@Coerce Object breakEvent, CallbackInfo ci) {
		com.dwurdy.lifepath.compat.forester.TimberTalesSupport.onTreeFelled(breakEvent);
	}
}
