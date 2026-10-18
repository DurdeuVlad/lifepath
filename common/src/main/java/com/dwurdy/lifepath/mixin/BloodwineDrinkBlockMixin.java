package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.bloodwine.BloodwineTakeSupport;
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
 * Kaleidoscope Bloodwine drink-block take-out → alcohol XP (NeoForge-only,
 * gated by the {@code lifepath.compat.kaleidoscope_bloodwine.mixins.json}
 * config's {@code requiredMods}). Verified against bloodwine 1.1.0 bytecode:
 * every drink block in the mod is a {@code BloodDrinkBlock} whose
 * {@code useItemOn} serves a glass to an empty hand — it calls
 * {@code BloodDrinkBlockEntity.removeItem()} and hands the stack to the
 * player via {@code ItemHandlerHelper.giveItemToPlayer}. This is the REAL
 * take path: the barrel pour fills the placed drink block at TAIL of
 * {@code BarrelBlockEntity.doTapExtract} via bloodwine's own mixin, which
 * can bypass the tap-side output-handler delta the existing
 * {@link KaleidoscopeTavernBarrelTapMixin} watches — a drink block below
 * the tap (a non-carrier) is filled without any handler decrement. One
 * wrap covers every blood wine (all drinks share this block class).
 * Attributed to whoever takes the serving.
 */
@Pseudo
@Mixin(targets = "com.github.kaleidoscope_bloodwine.block.BloodDrinkBlock",
		remap = false)
public abstract class BloodwineDrinkBlockMixin {

	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
			target = "Lcom/github/kaleidoscope_bloodwine/blockentity/BloodDrinkBlockEntity;"
					+ "removeItem()Lnet/minecraft/world/item/ItemStack;"),
			remap = false, require = 0)
	private ItemStack lifepath$emitOnTake(@Coerce Object drinkBe,
			Operation<ItemStack> original, ItemStack heldStack, BlockState state,
			Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		ItemStack removed = original.call(drinkBe);
		if (!level.isClientSide()) {
			BloodwineTakeSupport.emitBrewedTake(player, removed);
		}
		return removed;
	}
}
