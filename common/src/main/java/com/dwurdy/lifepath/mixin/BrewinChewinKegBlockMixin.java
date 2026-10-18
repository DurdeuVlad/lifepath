package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Brewin' And Chewin' keg pours → alcohol XP, in-world path (NeoForge-only,
 * gated by the {@code lifepath.compat.brewinandchewin.mixins.json} config's
 * {@code requiredMods}). Verified against BnC 4.5.0 bytecode:
 * {@code KegBlock.useItemOn} calls {@code KegBlockEntity.extractInWorld(
 * ItemStack, int, boolean)} and hands every produced stack to the interacting
 * player — the keg has no vanilla-style result slot, so the take is wrapped at
 * the call site where the player is still in scope. Attributed to the pourer.
 */
@Pseudo
@Mixin(targets = "umpaz.brewinandchewin.common.block.KegBlock", remap = false)
public abstract class BrewinChewinKegBlockMixin {

	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE",
			target = "Lumpaz/brewinandchewin/common/block/entity/KegBlockEntity;"
					+ "extractInWorld(Lnet/minecraft/world/item/ItemStack;IZ)"
					+ "Ljava/util/List;"),
			remap = false, require = 0)
	private List<ItemStack> lifepath$emitOnExtract(@Coerce Object keg, ItemStack container, int count,
			boolean creative, Operation<List<ItemStack>> original, ItemStack stack,
			BlockState state, Level level, BlockPos pos, Player player) {
		List<ItemStack> produced = original.call(keg, container, count, creative);
		if (produced != null) {
			for (ItemStack s : produced) {
				KaleidoscopeTakeSupport.emitTake(player, s);
			}
		}
		return produced;
	}
}
