package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.potionbundles.PotionBundleBrewing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PotionBundles brewing-facet XP seam (NeoForge-only, gated by the {@code
 * lifepath.compat.potionbundles.mixins.json} config's {@code requiredMods}).
 * {@code PotionBundleRecipe} is a crafting-grid {@code CustomRecipe} — the
 * bundle take funnels through vanilla's {@code ResultSlot.onTake}, the same
 * place {@code CraftingResultSlotMixin} already pays CRAFTING. This second
 * hook on the same method adds the BREWING emit for stacks in the
 * {@code potionbundles} namespace only; everything else returns early.
 * No {@link org.spongepowered.asm.mixin.Pseudo} — the target is vanilla.
 */
@Mixin(ResultSlot.class)
public abstract class PotionBundlesResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("TAIL"))
	private void lifepath$bundleBrewing(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			PotionBundleBrewing.onBundleTake(player, stack);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"potionbundles brewing seam failed", t);
		}
	}
}
