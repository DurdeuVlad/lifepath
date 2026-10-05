package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Farmer's Delight skillet takes → Cooking XP (NeoForge-only,
 * {@code requiredMods}-gated). The skillet stores one stack while it cooks;
 * the next interaction pulls it out via {@code SkilletBlockEntity.removeItem()}
 * — a custom block-entity handoff, so again no vanilla event exists. We hook
 * the {@code removeItem} call inside {@code useItemOn} and read the still-
 * stored stack via the BE's public getter; whoever interacted gets the XP.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.SkilletBlock", remap = false)
public abstract class FarmersDelightSkilletMixin {

	@Inject(method = "useItemOn",
			at = @At(value = "INVOKE",
					target = "Lvectorwing/farmersdelight/common/block/entity/SkilletBlockEntity;removeItem()Lnet/minecraft/world/item/ItemStack;"),
			remap = false, require = 0)
	private void lifepath$skilletTake(ItemStack held, BlockState state, Level level,
			BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
			CallbackInfoReturnable<ItemInteractionResult> cir) {
		if (level.isClientSide()) {
			return;
		}
		try {
			BlockEntity be = level.getBlockEntity(pos);
			if (be == null) {
				return;
			}
			Object stored = be.getClass().getMethod("getStoredStack").invoke(be);
			if (stored instanceof ItemStack stack && !stack.isEmpty()) {
				VanillaGameplayProducers.onCraftOutput(player, stack);
			}
		} catch (ReflectiveOperationException e) {
			LifepathMod.LOGGER.debug("Farmer's Delight skillet XP hook degraded: {}",
					e.toString());
		}
	}
}
