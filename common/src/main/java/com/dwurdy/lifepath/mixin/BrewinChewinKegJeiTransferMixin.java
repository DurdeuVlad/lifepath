package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Brewin' And Chewin' keg pours → alcohol XP, JEI recipe-transfer path
 * (NeoForge-only, gated by the {@code lifepath.compat.brewinandchewin
 * .mixins.json} config's {@code requiredMods}). Verified against BnC 4.5.0
 * bytecode: {@code FermentingTransferServer.setItems(Player, ...)} drains
 * the keg tank into containers via {@code extractFromFluidTank} (two call
 * sites) and stows the produced stacks into the player's inventory. The
 * produced list is captured at the call site where the {@link Player} is in
 * scope. Attributed to that player.
 */
@Pseudo
@Mixin(targets = "umpaz.brewinandchewin.integration.jei.transfer.FermentingTransferServer",
		remap = false)
public abstract class BrewinChewinKegJeiTransferMixin {

	@WrapOperation(method = "setItems", at = @At(value = "INVOKE",
			target = "Lumpaz/brewinandchewin/integration/jei/transfer/FermentingTransferServer;"
					+ "extractFromFluidTank(Lnet/minecraft/world/level/Level;"
					+ "Ljava/util/List;"
					+ "Lumpaz/brewinandchewin/common/block/entity/container/KegMenu;"
					+ "ZLumpaz/brewinandchewin/common/crafting/KegFermentingRecipe;)"
					+ "Ljava/util/List;"),
			remap = false, require = 0)
	private static List<ItemStack> lifepath$emitOnExtract(Level level, List<ItemStack> containers,
			@Coerce Object menu, boolean forRecipe, @Coerce Object recipe, Operation<List<ItemStack>> original,
			Player player) {
		List<ItemStack> produced = original.call(level, containers, menu, forRecipe, recipe);
		if (produced != null) {
			for (ItemStack s : produced) {
				KaleidoscopeTakeSupport.emitTake(player, s);
			}
		}
		return produced;
	}
}
