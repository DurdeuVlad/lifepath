package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Naturalist whistle-command seam (LP-INT-2), gated by
 * {@code lifepath.compat.naturalist.mixins.json}'s {@code requiredMods}.
 *
 * <p>{@code FollowingPet.tryWhistle} is the static funnel every Naturalist
 * pet's {@code mobInteract} calls first when the held item is the
 * Naturalist whistle — it toggles follow/stay and returns a consumed
 * result <em>before</em> the entity delegates to
 * {@code Animal.mobInteract}, so the vanilla feed/care TAIL never sees the
 * act. A pet command is animal handling → {@code lifepath:cared}.
 *
 * <p>The target is an <em>interface</em> holding the static {@code tryWhistle}
 * — mixin type must match (an interface mixin), so this file is an interface.
 */
@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.server.entity.base.FollowingPet",
		remap = false)
public interface NaturalistWhistleMixin {

	@Inject(method = "tryWhistle", at = @At("RETURN"), require = 0)
	private static void lifepath$natWhistled(TamableAnimal pet, Player player,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction()) {
			HusbandryProducers.onMobCared(player, pet);
		}
	}
}
