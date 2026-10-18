package com.dwurdy.lifepath.mixin;

import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@link BreedGoal}'s protected breeding pair fields —
 * needed by the Naturalist compat mixin
 * ({@code NaturalistEggBreedMixin}) which emits
 * {@code lifepath:bred} for the egg-laying breed path that bypasses
 * {@code Animal.spawnChildFromBreeding}.
 */
@Mixin(BreedGoal.class)
public interface BreedGoalAccessor {

	@Accessor("animal")
	Animal lifepath$animal();

	@Accessor("partner")
	Animal lifepath$partner();
}
