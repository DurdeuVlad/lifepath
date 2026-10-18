package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Naturalist egg-laying breed seam (LP-INT-2), gated by
 * {@code lifepath.compat.naturalist.mixins.json}'s {@code requiredMods}.
 *
 * <p>{@code EggLayingBreedGoal.breed} replaces the whole
 * {@code spawnChildFromBreeding} body: it awards the vanilla bred-animal
 * trigger, sets {@code EggLayingAnimal.setHasEgg(true)}, resets love and
 * drops XP — but never spawns a child and never sets the breed cooldown, so
 * the base {@code bred}/{@code born} seams stay blind for ostrich/snail
 * pairings. A completed {@code breed} call is a resolved pairing →
 * {@link HusbandryProducers#onExternalBreeding} emits {@code lifepath:bred}
 * attributed through the recorded keeper interactions.
 *
 * <p>The pair is read through {@link BreedGoalAccessor} — the goal's
 * {@code animal}/{@code partner} are {@code BreedGoal}'s protected fields.
 */
@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.server.entity.ai.goal.EggLayingBreedGoal",
		remap = false)
public abstract class NaturalistEggBreedMixin {

	@Inject(method = "breed", at = @At("TAIL"), require = 0)
	private void lifepath$natEggBred(CallbackInfo ci) {
		BreedGoalAccessor goal = (BreedGoalAccessor) this;
		HusbandryProducers.onExternalBreeding(goal.lifepath$animal(),
				goal.lifepath$partner());
	}
}
