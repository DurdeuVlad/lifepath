package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.morph.MorphDisguise;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M-5 station gate: furnace/blast/smoker output extraction is denied while
 * morphed. The GUI can't be opened at all while morphed (block interact is
 * refused), so this covers the already-open-at-morph edge.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoFurnaceTake(Player player, ItemStack stack,
			CallbackInfo ci) {
		if (MorphDisguise.isDisguised(player)) {
			ci.cancel();
		}
	}
}
