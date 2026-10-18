package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.kaleidoscope.KaleidoscopeTakeSupport;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Brewin' And Chewin' keg pours → alcohol XP, EMI pour-recipe path
 * (NeoForge-only, gated by the {@code lifepath.compat.brewinandchewin
 * .mixins.json} config's {@code requiredMods}). Verified against BnC 4.5.0
 * bytecode: {@code EMIFillPouringRecipeServerboundPacket.handle(ServerPlayer)}
 * defers to a {@code lambda$handle$0} that fills containers via
 * {@code KegBlockEntity.extractInGui} and stows the output into the keg menu's
 * output slot or the player's inventory. Wrapped at the call site where the
 * {@link ServerPlayer} is in scope; if a BnC update renames the lambda this
 * hook silently skips rather than failing. Attributed to the pouring player.
 */
@Pseudo
@Mixin(targets = "umpaz.brewinandchewin.common.network.serverbound.EMIFillPouringRecipeServerboundPacket",
		remap = false)
public abstract class BrewinChewinKegEmiPourMixin {

	@WrapOperation(method = "lambda$handle$0", at = @At(value = "INVOKE",
			target = "Lumpaz/brewinandchewin/common/block/entity/KegBlockEntity;"
					+ "extractInGui(Lnet/minecraft/world/item/ItemStack;I)"
					+ "Ljava/util/List;"),
			remap = false, require = 0)
	private List<ItemStack> lifepath$emitOnExtract(@Coerce Object keg, ItemStack container, int count,
			Operation<List<ItemStack>> original, ServerPlayer player) {
		List<ItemStack> produced = original.call(keg, container, count);
		if (produced != null) {
			for (ItemStack s : produced) {
				KaleidoscopeTakeSupport.emitTake(player, s);
			}
		}
		return produced;
	}
}
