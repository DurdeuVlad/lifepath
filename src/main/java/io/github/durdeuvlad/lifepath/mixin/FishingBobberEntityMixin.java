package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Emits a fishing activity per caught item. The hook point is the
 * {@code ItemEntity.<init>} call inside {@code FishingBobberEntity.use} —
 * it executes only on the successful-catch branch (loot generated, entity
 * being spawned); casts and entity-hook pulls never reach it.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {

	@Shadow
	public abstract PlayerEntity getPlayerOwner();

	@Inject(method = "use(Lnet/minecraft/item/ItemStack;)I",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/entity/ItemEntity;<init>(Lnet/minecraft/world/World;DDDLnet/minecraft/item/ItemStack;)V"))
	private void lifepath$onCatch(ItemStack usedItem, CallbackInfoReturnable<Integer> cir,
			World world, double x, double y, double z, ItemStack caught) {
		VanillaGameplayProducers.onFishCaught(getPlayerOwner(), caught);
	}
}
