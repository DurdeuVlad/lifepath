package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes successful block placements so Lifepath can (a) record the position
 * in {@code PlacedBlockTracker} (re-mine exploit mitigation) and (b) emit a
 * farming planting event for crop blocks. Server-side effects only.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

	@Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN"))
	private void lifepath$onPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = cir.getReturnValue();
		Player player = context.getPlayer();
		if (result.consumesAction() && player instanceof ServerPlayer serverPlayer
				&& context.getLevel() instanceof ServerLevel world) {
			BlockState placed = context.getLevel().getBlockState(context.getClickedPos());
			VanillaGameplayProducers.onBlockPlaced(serverPlayer, world,
					context.getClickedPos(), placed);
		}
	}
}
