package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Naturalist hand-catch seam (LP-INT-2), gated by
 * {@code lifepath.compat.naturalist.mixins.json}'s {@code requiredMods}.
 *
 * <p>{@code Catchable.catchAnimal} is the static funnel Naturalist critters
 * (snail, crab, ant, scorpion, caterpillar…) run when a player picks them
 * up into a held item. It resolves inside the entity's own
 * {@code mobInteract} without delegating to {@code Animal.mobInteract}, so
 * the base seams never see the consumed result. Catching a critter is
 * animal handling → {@code lifepath:cared}, emitted only when the returned
 * {@code Optional} holds a consumed result.
 *
 * <p>The target is an <em>interface</em> holding the static
 * {@code catchAnimal} — mixin type must match, so this file is an interface.
 */
@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.server.entity.base.Catchable",
		remap = false)
public interface NaturalistCatchableMixin {

	@Inject(method = "catchAnimal", at = @At("RETURN"), require = 0)
	private static void lifepath$natCaught(Player player, InteractionHand hand,
			LivingEntity mob, boolean swap,
			CallbackInfoReturnable<Optional<InteractionResult>> cir) {
		Optional<InteractionResult> result = cir.getReturnValue();
		if (result.isPresent() && result.get().consumesAction()) {
			HusbandryProducers.onMobCared(player, mob);
		}
	}
}
