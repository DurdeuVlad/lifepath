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
 * Smithing XP for alloy-furnace take-outs (NeoForge-only,
 * {@code requiredMods=["overgeared"]}). {@code AlloySmelterMenu$1} is the
 * output slot — a NeoForge {@code SlotItemHandler} subclass with its own
 * {@code onTake} (awards the smelter's stored experience), so no vanilla
 * slot hook ever fires. This is the documented-but-missing half of the
 * adapter's "alloy furnace outputs taken from menus" coverage: alloyed
 * ingots (steel, etc.) now publish a smithing take with outcome scaling.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.screen.AlloySmelterMenu$1",
		remap = false)
public abstract class OvergearedAlloySmelterResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$alloyOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			OvergearedStationSupport.emitTake(player, stack, "alloy_furnace");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("overgeared alloy smelter seam failed", t);
		}
	}
}
