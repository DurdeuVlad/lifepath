package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.farmersrespite.FarmersRespiteTakeSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Farmers Respite kettle container-pour → Brewing XP (NeoForge-only,
 * gated by the {@code lifepath.compat.farmersrespite.mixins.json} config's
 * {@code requiredMods}). Verified against farmersrespite 3.0.1 bytecode:
 * {@code KettleBlock.useItemOn} routes container interactions (cups,
 * bottles, jars) into {@code KettleBlockEntity.fluidExtract
 * (KettleBlockEntity, ItemStack, boolean)}, which either fills the held
 * container from the tank or drains the container in — the returned stack
 * is what the player receives. The emit fires only when the result is one
 * of the mod's registered drink items ({@code isKnownDrinkItem}), so
 * pouring tea/coffee/cider pays while dumping an ingredient or drawing
 * plain water earns nothing. Attributed to the interacting player.
 */
@Pseudo
@Mixin(targets = "com.farmersrespite.common.block.KettleBlock", remap = false)
public abstract class FarmersRespiteKettleBlockMixin {

	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
			target = "Lcom/farmersrespite/common/block/entity/KettleBlockEntity;"
					+ "fluidExtract(Lcom/farmersrespite/common/block/entity/KettleBlockEntity;"
					+ "Lnet/minecraft/world/item/ItemStack;Z)"
					+ "Lnet/minecraft/world/item/ItemStack;"),
			remap = false, require = 0)
	private ItemStack lifepath$emitOnPour(@Coerce Object kettle,
			@Coerce Object kettleArg, ItemStack container, boolean flag,
			Operation<ItemStack> original, ItemStack heldStack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		ItemStack result = original.call(kettle, kettleArg, container, flag);
		if (!level.isClientSide()) {
			FarmersRespiteTakeSupport.emitIfDrinkTake(player, kettle, result);
		}
		return result;
	}
}
