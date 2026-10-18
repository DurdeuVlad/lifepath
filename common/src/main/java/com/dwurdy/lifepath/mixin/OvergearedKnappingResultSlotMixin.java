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
 * Smithing XP for rock-knapping take-outs (NeoForge-only, gated by
 * {@code lifepath.compat.overgeared.mixins.json}'s {@code requiredMods}).
 * The knapping menu's result slot is the anonymous
 * {@code RockKnappingMenu$2} — a plain {@code Slot} subclass whose
 * {@code onTake} calls {@code Slot.onTake} (a no-op), never
 * {@code ResultSlot.onTake}, so the vanilla crafting-take seam can't see
 * knapped heads/blades (including overgearedspartan's stone parts, whose
 * 22 knapping recipes run on this station). Both click and shift-click
 * funnel through this {@code onTake} ({@code quickMoveStack} delegates to
 * it). Emitted on the smithing lane so outcome bands can stamp forging
 * quality on the part.
 */
@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.screen.RockKnappingMenu$2",
		remap = false)
public abstract class OvergearedKnappingResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$knappingOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			OvergearedStationSupport.emitTake(player, stack, "rock_knapping");
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("overgeared knapping seam failed", t);
		}
	}
}
