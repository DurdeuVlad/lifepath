package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.productivefarming.ProductiveFarmingSupport;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Productive Farming composter side-plant seam (LP-INT-2), gated by
 * {@code lifepath.compat.productivefarming.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code EventHandler.itemUseEvent} (its {@code UseItemOnBlockEvent}
 * handler) plants a mushroom-growth block beside a composter when the held
 * item is a tagged mushroom — placed with a bare
 * {@code ServerLevel.setBlockAndUpdate}, so the vanilla placement seam sees
 * nothing. One wrapped set-block = one planted substrate block →
 * {@link VanillaGameplayProducers#onCompatPlanted} emits
 * {@code lifepath:planted}.
 */
@Pseudo
@Mixin(targets = "cy.jdkdigital.productivefarming.event.EventHandler",
		remap = false)
public abstract class ProductiveFarmingComposterMixin {

	@WrapOperation(method = "itemUseEvent",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
			require = 0)
	private static boolean lifepath$pfComposterPlanted(ServerLevel level,
			BlockPos pos, BlockState state, Operation<Boolean> original,
			@Coerce Object event) {
		boolean result = original.call(level, pos, state);
		if (result && ProductiveFarmingSupport.eventPlayer(event)
				instanceof ServerPlayer sp) {
			VanillaGameplayProducers.onCompatPlanted(sp, level, pos, state);
		}
		return result;
	}
}
