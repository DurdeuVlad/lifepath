package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Productive Farming seed-bag planting seam (LP-INT-2), gated by
 * {@code lifepath.compat.productivefarming.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code SeedBagItem.useOn} plants a block per farmland position via its
 * private {@code lambda$useOn$0} consumer, which calls
 * {@code Level.setBlockAndUpdate} directly — never {@code BlockItem.place} —
 * so the vanilla placement seam (and {@code PlacedBlockTracker}) see none
 * of it. One wrapped set-block = one planted crop →
 * {@link VanillaGameplayProducers#onCompatPlanted} emits
 * {@code lifepath:planted} with the bag's own crop state and records the
 * position against the re-mine exploit.
 */
@Pseudo
@Mixin(targets = "cy.jdkdigital.productivefarming.common.item.SeedBagItem",
		remap = false)
public abstract class ProductiveFarmingSeedBagMixin {

	@WrapOperation(method = "lambda$useOn$0",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
			require = 0)
	private static boolean lifepath$pfBagPlanted(Level level, BlockPos pos,
			BlockState state, Operation<Boolean> original, UseOnContext context,
			BlockItem seed, AtomicInteger placed, BlockPos target) {
		boolean result = original.call(level, pos, state);
		if (result && context.getPlayer() instanceof ServerPlayer sp
				&& level instanceof ServerLevel world) {
			VanillaGameplayProducers.onCompatPlanted(sp, world, pos, state);
		}
		return result;
	}
}
