package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bucket-catch seam on {@code Axolotl.mobInteract} — see
 * {@link BucketablePickupMixin}. The axolotl is the only vanilla
 * {@code Bucketable} caller of {@code Bucketable.bucketMobPickup}
 * outside the {@code AbstractFish} tree.
 */
@Mixin(Axolotl.class)
public abstract class AxolotlBucketPickupMixin {

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
