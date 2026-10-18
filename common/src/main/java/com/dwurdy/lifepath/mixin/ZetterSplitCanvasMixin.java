package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.zetter.ZetterArtistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scholarship seam for Zetter's artist table split mode — each
 * {@code ArtistTableMenu$SlotSplitGrid} slot whose {@code onTake} funnels
 * into {@code AbstractCanvasAction.onTakeSplit}: the moment a sliced piece
 * of a larger canvas becomes its own {@code zetter:canvas} item.
 */
@Pseudo
@Mixin(targets = "me.dantaeusb.zetter.menu.ArtistTableMenu$SlotSplitGrid",
		remap = false)
public abstract class ZetterSplitCanvasMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$splitCanvasOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			ZetterArtistry.emitTake(player, stack);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("zetter split-canvas seam failed", t);
		}
	}
}
