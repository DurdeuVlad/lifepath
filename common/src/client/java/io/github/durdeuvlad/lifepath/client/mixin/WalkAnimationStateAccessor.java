package io.github.durdeuvlad.lifepath.client.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * M-4: field access into {@link WalkAnimationState} so the disguise entity
 * can mirror the morphed player's exact limb phase — the state is private
 * and the interpolated render getters ({@code position(partialTick)},
 * {@code speed(partialTick)}) depend on all three fields.
 */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
	@Accessor("position")
	float lifepath$position();

	@Accessor("position")
	void lifepath$setPosition(float position);

	@Accessor("speed")
	float lifepath$speed();

	@Accessor("speed")
	void lifepath$setSpeed(float speed);

	@Accessor("speedOld")
	float lifepath$speedOld();

	@Accessor("speedOld")
	void lifepath$setSpeedOld(float speedOld);
}
