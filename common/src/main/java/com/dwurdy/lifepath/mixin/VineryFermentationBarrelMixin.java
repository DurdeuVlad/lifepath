package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Let's Do Vinery fermentation-barrel takes → alcohol XP (NeoForge-only,
 * gated by the {@code lifepath.compat.vinery.mixins.json} config's
 * {@code requiredMods}). Verified against vinery 1.5.4 bytecode: the output
 * slot is a custom {@code Slot} subclass
 * ({@code FermentationBarrelOutputSlot}) whose {@code onTake} is invoked for
 * click-takes and shift-click alike ({@code FermentationBarrelGuiHandler
 * .quickMoveStack} → {@code Slot.onTake}), so this single hook covers both.
 * Attributed to whoever takes the wine. (The apple press uses a vanilla
 * {@code FurnaceResultSlot} — already covered by
 * {@link FurnaceResultSlotMixin}; no seam needed there.)
 */
@Pseudo
@Mixin(targets = "net.satisfy.vinery.client.gui.handler.slot.FermentationBarrelOutputSlot",
		remap = false)
public abstract class VineryFermentationBarrelMixin {

	@Inject(method = "onTake", at = @At("TAIL"), remap = false, require = 0)
	private void lifepath$fermentationTake(Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
