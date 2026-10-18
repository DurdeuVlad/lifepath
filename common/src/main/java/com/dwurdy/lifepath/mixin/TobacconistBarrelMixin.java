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
 * Tobacconist barrel take-outs → crafting XP (NeoForge-only, gated by the
 * {@code lifepath.compat.tobacconistmod.mixins.json} config's
 * {@code requiredMods}). Verified against tobacconistmod 4.0.0 bytecode:
 * {@code TobaccoBarrelBlock.useItemOn}/{@code useWithoutItem} both funnel
 * into the private {@code handleUse}, whose empty-hand branch calls
 * {@code TobaccoBarrelBlockEntity.removeStoredTobacco()} and hands the
 * stack to the player. The barrel also stores unprocessed leaf, so the
 * emit is gated on the mod's own quality markers —
 * {@code isFermented(ItemStack)} or {@code getAgedDays(ItemStack) > 0}
 * (both public statics on the block entity) — withdrawing tobacco that
 * never finished a process pays nothing. Hopper extraction rides
 * {@code extractTobaccoAutomated}/{@code IItemHandler}, not this path, so
 * automation earns nothing. Attributed to whoever takes the tobacco.
 */
@Pseudo
@Mixin(targets = "com.diggydwarff.tobacconistmod.block.custom.TobaccoBarrelBlock",
		remap = false)
public abstract class TobacconistBarrelMixin {

	@WrapOperation(method = "handleUse", at = @At(value = "INVOKE",
			target = "Lcom/diggydwarff/tobacconistmod/block/entity/TobaccoBarrelBlockEntity;"
					+ "removeStoredTobacco()Lnet/minecraft/world/item/ItemStack;"),
			remap = false, require = 0)
	private ItemStack lifepath$emitOnTake(@Coerce Object barrel,
			Operation<ItemStack> original, BlockState state, Level level,
			BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		ItemStack taken = original.call(barrel);
		if (!level.isClientSide() && taken != null && !taken.isEmpty()
				&& (Boolean.TRUE.equals(TobacconistTakeSupport
						.invokeStatic(barrel, "isFermented", taken))
						|| agedDays(barrel, taken) > 0)) {
			TobacconistTakeSupport.emitTake(player, taken);
		}
		return taken;
	}

	private static int agedDays(Object barrel, ItemStack stack) {
		Object days = TobacconistTakeSupport.invokeStatic(barrel, "getAgedDays", stack);
		return days instanceof Integer i ? i : 0;
	}
}
