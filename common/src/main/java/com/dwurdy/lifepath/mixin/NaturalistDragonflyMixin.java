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
 * Naturalist dragonfly feed seam (LP-INT-2), gated by
 * {@code lifepath.compat.naturalist.mixins.json}'s {@code requiredMods}.
 *
 * <p>The dragonfly is a {@code PathfinderMob}, not an {@code Animal} — its
 * chorus-fruit feed (which sets up the dragon's-breath cloud capture) never
 * passes through the vanilla feed seam. A consumed interact with a held
 * item = the feed → {@code lifepath:fed}.
 */
@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.server.entity.mob.Dragonfly",
		remap = false)
public abstract class NaturalistDragonflyMixin {

	@Inject(method = "mobInteract", at = @At("TAIL"), require = 0)
	private void lifepath$natDragonflyFed(Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		// Verified against naturalist 2.0.4 bytecode: the feed branch is
		// gated on Items.CHORUS_FRUIT. Other consumed interacts (glass-jar
		// capture) are catches, not feeds — gate on the same item so the
		// marker stays honest.
		if (!cir.getReturnValue().consumesAction()
				|| !player.getItemInHand(hand).is(Items.CHORUS_FRUIT)) {
			return;
		}
		HusbandryProducers.onMobFed(player, (LivingEntity) (Object) this);
	}
}
