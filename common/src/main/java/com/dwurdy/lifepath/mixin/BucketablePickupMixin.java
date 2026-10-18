package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bucket-catch seam on {@link Bucketable#bucketMobPickup} (LP-INT-2). The
 * static interface helper is the funnel every water-bucket mob pickup runs
 * through — vanilla fish/tadpoles ({@code AbstractFish.mobInteract}),
 * Naturalist's {@code VariantBucketable} starfish/duck, and any other modded
 * {@code Bucketable}. The Animal-subclass catches also resolve here before
 * {@code Animal.mobInteract} returns, but that base TAIL only sees the
 * Mob-side result for Animal subclasses — non-Animal {@code Bucketable}s
 * (fish, starfish) never reach it at all, so this seam is the only surface
 * for them.
 *
 * <p>Bucket-catching a live mob is animal handling → {@code lifepath:cared}.
 * Emits only when the returned {@code Optional} holds a consumed result.
 * FakePlayer deployers are filtered by the producer.
 */
@Mixin(Bucketable.class)
public interface BucketablePickupMixin {

	@Inject(method = "bucketMobPickup", at = @At("RETURN"))
	private static void lifepath$bucketCaught(Player player, InteractionHand hand,
			LivingEntity entity, CallbackInfoReturnable<Optional<InteractionResult>> cir) {
		Optional<InteractionResult> result = cir.getReturnValue();
		if (result.isPresent() && result.get().consumesAction()) {
			HusbandryProducers.onMobCared(player, entity);
		}
	}
}
