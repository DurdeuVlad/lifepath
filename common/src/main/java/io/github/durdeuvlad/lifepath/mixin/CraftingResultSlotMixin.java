package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a crafting activity when a player takes the result stack from a
 * crafting slot (crafting table, inventory grid, crafter UI). Fires only on
 * manual player extraction — crafter/hopper automation never calls this path.
 *
 * <p>M-5 morph gate: a morphed player can't take crafting output at all —
 * the deny lives here so the 2×2 inventory grid and the crafting table are
 * covered by one seam.
 */
@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), cancellable = true)
	private void lifepath$craftingOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		if (io.github.durdeuvlad.lifepath.morph.MorphDisguise
				.isDisguised(player)) {
			ci.cancel();
			return;
		}
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
