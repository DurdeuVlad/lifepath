package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M24 kill-loot seam: {@code LivingEntity.dropFromLootTable} funnels every
 * entity's death loot through {@code LootTable.getRandomItems(LootParams,
 * long, Consumer)} — stacks arrive one at a time through the consumer, so we
 * wrap the consumer rather than mutate a returned list. The victim is the
 * mixin instance; the killer comes from the enclosing method's
 * {@link DamageSource} (projectile sources resolve to the shooter via
 * {@code getEntity()}).
 */
@Mixin(LivingEntity.class)
public abstract class EntityLootOutcomeMixin {

	@WrapOperation(
			method = "dropFromLootTable",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;JLjava/util/function/Consumer;)V"))
	private void lifepath$scaleKillDrops(LootTable table, LootParams params,
			long seed, Consumer<ItemStack> consumer, Operation<Void> original,
			DamageSource source, boolean hitByPlayer) {
		LivingEntity victim = (LivingEntity) (Object) this;
		original.call(table, params, seed,
				VanillaGameplayProducers.wrapKillDropConsumer(victim, source, consumer));
	}
}
