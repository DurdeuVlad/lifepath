package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bucket-catch seam on {@code AbstractFish.mobInteract} (LP-INT-2).
 * {@code Bucketable.bucketMobPickup} is a static interface helper — Mixin
 * rejects injectors inside interface mixins outright, so the funnel is
 * unreachable from the interface side and every caller must be wrapped
 * where it invokes the helper. This target covers all vanilla fish and
 * tadpoles ({@code Tadpole} inherits {@code mobInteract}); the axolotl
 * has its own {@code mobInteract} and lives in
 * {@link AxolotlBucketPickupMixin}. Modded {@code Bucketable}s that call
 * the helper from their own {@code mobInteract} are out of reach here —
 * Naturalist's catchables already emit {@code cared} through their own
 * compat mixins.
 *
 * <p>Bucket-catching a live mob is animal handling → {@code lifepath:cared}.
 * Emits only when the returned {@code Optional} holds a consumed result.
 * FakePlayer deployers are filtered by the producer.
 */
@Mixin(AbstractFish.class)
public abstract class BucketablePickupMixin {

	@WrapOperation(method = "mobInteract",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/animal/Bucketable;bucketMobPickup(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;"))
	private Optional<InteractionResult> lifepath$bucketCaught(Player player,
			InteractionHand hand, LivingEntity entity,
			Operation<Optional<InteractionResult>> original) {
		Optional<InteractionResult> result = original.call(player, hand, entity);
		if (result.isPresent() && result.get().consumesAction()) {
			HusbandryProducers.onMobCared(player, entity);
		}
		return result;
	}
}
