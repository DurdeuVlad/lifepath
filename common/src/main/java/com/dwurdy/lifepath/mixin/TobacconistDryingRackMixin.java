package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.tobacconist.TobacconistTakeSupport;
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
 * Tobacconist drying-rack take-outs → crafting XP (NeoForge-only, gated by
 * the {@code lifepath.compat.tobacconistmod.mixins.json} config's
 * {@code requiredMods}). Verified against tobacconistmod 4.0.0 bytecode:
 * {@code TobaccoDryingRackBlock.useItemOn}/{@code useWithoutItem} both
 * funnel into the private {@code handleUse}, whose empty-hand branch calls
 * {@code TobaccoDryingRackBlockEntity.removeAllLeaves()} and hands the
 * stack to the player. {@code removeAllLeaves} also returns the unprocessed
 * leaf when the rack is emptied early, so the emit is gated on the rack's
 * own {@code isFinished()} (stored leaf carries the {@code CureType}
 * marker) — taking back a still-drying leaf pays nothing. The private
 * {@code handleUse} is inherited by {@code IndustrialDryingRackBlock}, so
 * one wrap covers both racks; hopper extraction rides
 * {@code IItemHandler}, not this path, so automation earns nothing.
 * Attributed to whoever takes the leaves.
 */
@Pseudo
@Mixin(targets = "com.diggydwarff.tobacconistmod.block.custom.TobaccoDryingRackBlock",
		remap = false)
public abstract class TobacconistDryingRackMixin {

	@WrapOperation(method = "handleUse", at = @At(value = "INVOKE",
			target = "Lcom/diggydwarff/tobacconistmod/block/entity/TobaccoDryingRackBlockEntity;"
					+ "removeAllLeaves()Lnet/minecraft/world/item/ItemStack;"),
			remap = false, require = 0)
	private ItemStack lifepath$emitOnTake(@Coerce Object rack,
			Operation<ItemStack> original, BlockState state, Level level,
			BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		boolean finished = TobacconistTakeSupport.invokeBool(rack, "isFinished");
		ItemStack taken = original.call(rack);
		if (finished && !level.isClientSide()) {
			TobacconistTakeSupport.emitTake(player, taken);
		}
		return taken;
	}
}
