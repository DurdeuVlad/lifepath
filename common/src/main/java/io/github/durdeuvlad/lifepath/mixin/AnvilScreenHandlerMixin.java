package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a smithing activity when a player takes output from an anvil
 * (repair/combine/rename). Fires only through the result slot path —
 * there is no automated anvil output in vanilla.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilScreenHandlerMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"))
	private void lifepath$smithingOutput(Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onForgeOutput(player, stack,
				io.github.durdeuvlad.lifepath.LifepathMod.id("anvil"));
	}
}
