package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
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

	@Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
			at = @At("RETURN"))
	private void lifepath$onPlaced(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		ActionResult result = cir.getReturnValue();
		PlayerEntity player = context.getPlayer();
		if (result.isAccepted() && player instanceof ServerPlayerEntity serverPlayer
				&& context.getWorld() instanceof ServerWorld world) {
			BlockState placed = context.getWorld().getBlockState(context.getBlockPos());
			VanillaGameplayProducers.onBlockPlaced(serverPlayer, world,
					context.getBlockPos(), placed);
		}
	}
}
