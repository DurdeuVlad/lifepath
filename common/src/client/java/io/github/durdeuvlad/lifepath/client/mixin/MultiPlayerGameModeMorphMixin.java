package io.github.durdeuvlad.lifepath.client.mixin;

import io.github.durdeuvlad.lifepath.morph.MorphDisguise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M-5 client mirror of the server gates: while morphed, the interaction
 * packets are never sent at all — no arm swing, no door flicker, no
 * ghost-mining crack. The authoritative deny still lives server-side
 * ({@code ServerGameModeMorphMixin}); this just keeps client prediction
 * honest so nothing desyncs. Reads the same synced disguise flag.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMorphMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	private boolean lifepath$disguised() {
		return MorphDisguise.isDisguised(minecraft.player);
	}

	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoUse(Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (lifepath$disguised()) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoUseOn(LocalPlayer player, InteractionHand hand,
			BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (lifepath$disguised()) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoInteract(Player player, Entity entity,
			InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (lifepath$disguised()) {
			cir.setReturnValue(InteractionResult.PASS);
		}
	}
}
