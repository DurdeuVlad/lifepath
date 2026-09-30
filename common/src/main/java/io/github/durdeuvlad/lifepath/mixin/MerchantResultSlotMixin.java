package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.morph.MorphDisguise;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M-5 station gate: villager/wandering-trader trade output is denied while
 * morphed. Trading needs an entity interact to open in the first place —
 * this covers a trade GUI left open across a morph.
 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), cancellable = true)
	private void lifepath$morphNoTradeTake(Player player, ItemStack stack,
			CallbackInfo ci) {
		if (MorphDisguise.isDisguised(player)) {
			ci.cancel();
		}
	}
}
