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
 * Smithing XP for nether-alloy-furnace take-outs (NeoForge-only,
 * {@code requiredMods=["overgeared"]}). {@code NetherAlloySmelterMenu$2} is
 * the output slot — a {@code SlotItemHandler} subclass with its own
 * {@code onTake}, invisible to the vanilla slot mixins. Covers the
 * netherite-alloy lane of the alloy smelter gap.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.screen.NetherAlloySmelterMenu$2",
		remap = false)
public abstract class OvergearedNetherAlloySmelterResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$netherAlloyOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			OvergearedStationSupport.emitTake(player, stack,
					"nether_alloy_furnace");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug(
					"overgeared nether alloy smelter seam failed", t);
		}
	}
}
