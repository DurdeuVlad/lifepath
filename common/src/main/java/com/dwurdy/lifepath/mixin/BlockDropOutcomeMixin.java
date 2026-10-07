package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * M24 drop seam: {@code Block.getDrops} carries both the broken block's
 * state and the breaking entity — one hook covers mining, woodcutting,
 * foraging, and farming-harvest yield scaling. Non-player breakers
 * (explosions, automation) carry no ServerPlayer and fall through.
 */
@Mixin(Block.class)
public abstract class BlockDropOutcomeMixin {

	@Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
			at = @At("RETURN"))
	private static void lifepath$scaleBlockDrops(BlockState state, ServerLevel level,
			BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool,
			CallbackInfoReturnable<List<ItemStack>> cir) {
		if (entity instanceof ServerPlayer player) {
			VanillaGameplayProducers.onBlockDrops(player, pos, state, cir.getReturnValue());
		}
	}
}
