package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Hexalia take-one-out interactions → craft-output activity (NeoForge-only,
 * gated by the {@code lifepath.compat.hexalia.mixins.json} config's
 * {@code requiredMods}). Verified against hexalia 1.3.7 bytecode: {@code
 * MortarAndPestleBlock}, {@code CenserBlock} and {@code SmallCauldronBlock}
 * all route empty-hand extraction through {@code
 * ItemInteractionHelper.tryExtractOneItem(Level, BlockPos, Player,
 * ItemInteractionHelper$ItemStorage)}, which calls {@code
 * ItemStorage.removeItem()} server-side only (the client side early-returns
 * before the call) and hands the produced stack to the player — or drops it
 * at the block. Wrapping that call captures exactly what was taken,
 * attributed to the taker.
 *
 * <p>For the mortar this is the production take-out ({@code
 * MortarAndPestleBlockEntity.removeItem()} → {@code takeOutputOne()} when an
 * output exists). The same seam also fires for cauldron/censer ingredient
 * take-back — intentionally unfiltered per the station audit: the emitted
 * crafting event is discriminated by xp_source data on item id/tags, so
 * mortar outputs can pay herbalist/foraging/brewing while reclaimed raw
 * ingredients pay nothing unless data says otherwise.
 */
@Pseudo
@Mixin(targets = "net.astralya.hexalia.util.ItemInteractionHelper", remap = false)
public abstract class HexaliaItemInteractionMixin {

	@WrapOperation(method = "tryExtractOneItem",
			at = @At(value = "INVOKE",
					target = "Lnet/astralya/hexalia/util/ItemInteractionHelper$ItemStorage;removeItem()Lnet/minecraft/world/item/ItemStack;"),
			remap = false, require = 0)
	private static ItemStack lifepath$emitExtracted(@Coerce Object storage,
			Operation<ItemStack> original, Level level, BlockPos pos,
			Player player, @Coerce Object itemStorage) {
		ItemStack removed = original.call(storage);
		KaleidoscopeTakeSupport.emitTake(player, removed);
		return removed;
	}
}
