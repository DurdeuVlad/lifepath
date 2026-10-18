package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Naturalist clam pearl-take seam (LP-INT-2), gated by
 * {@code lifepath.compat.naturalist.mixins.json}'s {@code requiredMods}.
 *
 * <p>Naturalist's {@code Clam} is a {@code WaterAnimal} — not an
 * {@code Animal} — so the vanilla {@code mobInteract} seam never observes
 * it. Its own {@code mobInteract} hands the grown pearl to the player and
 * returns a consumed result. Taking produce from a live mob is the milked
 * family → {@code lifepath:milked}. The debug-stick open/close path also
 * consumes but is gated out (creative-only tooling).
 */
@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.server.entity.mob.Clam",
		remap = false)
public abstract class NaturalistClamMixin {

	@Inject(method = "mobInteract", at = @At("TAIL"), require = 0)
	private void lifepath$natClamTaken(Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction()
				|| player.getItemInHand(hand).is(Items.DEBUG_STICK)) {
			return;
		}
		HusbandryProducers.onMobProductTaken(player, (LivingEntity) (Object) this);
	}
}
