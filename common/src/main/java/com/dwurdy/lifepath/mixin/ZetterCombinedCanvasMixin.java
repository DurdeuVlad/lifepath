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
 * Scholarship seam for Zetter's artist table — the combined-canvas result
 * slot ({@code ArtistTableMenu$SlotCombined}) whose {@code onTake} funnels
 * into {@code AbstractCanvasAction.onTakeCombined}: the moment stitched
 * canvases become one bigger {@code zetter:canvas}. Plain {@code Slot} (a
 * NeoForge {@code SlotItemHandler}), so the vanilla {@code ResultSlot}
 * take-out never saw it.
 */
@Pseudo
@Mixin(targets = "me.dantaeusb.zetter.menu.ArtistTableMenu$SlotCombined",
		remap = false)
public abstract class ZetterCombinedCanvasMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$combinedCanvasOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			ZetterArtistry.emitTake(player, stack);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("zetter combined-canvas seam failed", t);
		}
	}
}
