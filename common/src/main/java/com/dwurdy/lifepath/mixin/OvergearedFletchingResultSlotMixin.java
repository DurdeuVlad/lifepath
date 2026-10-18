package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.overgeared.OvergearedStationSupport;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smithing XP for fletching-station take-outs (NeoForge-only,
 * {@code requiredMods=["overgeared"]}). The station's output slot is the
 * anonymous {@code FletchingStationMenu$6} — a plain {@code Slot} over the
 * {@code ResultContainer} whose {@code onTake} consumes the inputs and
 * delegates to the {@code Slot} no-op, so tier-tagged arrow output
 * ({@code overgeared:iron_arrow}, {@code steel_arrow},
 * {@code diamond_arrow}) earned nothing before this seam. Covers click and
 * shift-click — {@code quickMoveStack} routes the result slot through
 * {@code onTake} too.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.screen.FletchingStationMenu$6",
		remap = false)
public abstract class OvergearedFletchingResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$fletchingOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			OvergearedStationSupport.emitTake(player, stack,
					"fletching_station");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("overgeared fletching seam failed", t);
		}
	}
}
