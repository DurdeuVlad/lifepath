package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.romaniansdelight.RomaniansDelightGrillSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RomaniansDelight grill cooks → Cooking XP (NeoForge-only, gated by the
 * {@code lifepath.compat.romaniansdelight.mixins.json} config's
 * {@code requiredMods}). Verified against romaniansdelight 0.7.0 bytecode:
 * {@code GrillBlockEntity} is a campfire clone — {@code placeFood(Entity,
 * ItemStack, int)} loads raw food into the first free slot and the static
 * {@code cookTick} pops the finished {@code campfire_cooking} result as an
 * unattributed {@code Containers.dropItemStack} world drop. There is no
 * take slot, so attribution follows the barrel-tap precedent: the placer
 * is recorded when {@code placeFood} accepts food and consumed, in order,
 * by the cooked drops. Non-player placers are never recorded — food that
 * appears without a player earns nothing.
 */
@Pseudo
@Mixin(targets = "zkamper.romaniansdelight.common.block.entity.GrillBlockEntity",
		remap = false)
public abstract class RomaniansDelightGrillMixin {

	@Inject(method = "placeFood", at = @At("RETURN"), remap = false,
			require = 0)
	private void lifepath$recordPlacer(Entity placer, ItemStack stack,
			int cookTime, CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			RomaniansDelightGrillSupport.recordPlacer(this, placer);
		}
	}

	@WrapOperation(method = "cookTick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/Containers;dropItemStack"
					+ "(Lnet/minecraft/world/level/Level;DDD"
					+ "Lnet/minecraft/world/item/ItemStack;)V"),
			remap = false, require = 0)
	private static void lifepath$emitCookedDrop(Level dropLevel, double x,
			double y, double z, ItemStack stack, Operation<Void> original,
			Level level, BlockPos pos, BlockState state, @Coerce Object grill) {
		original.call(dropLevel, x, y, z, stack);
		RomaniansDelightGrillSupport.emitCookedDrop(level, pos, stack);
	}
}
