package io.github.durdeuvlad.lifepath.mixin;

import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits a smithing activity when a player takes output from an anvil
 * (repair/combine/rename). Fires only through the result slot path —
 * there is no automated anvil output in vanilla.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {

	@Inject(method = "onTakeOutput(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V",
			at = @At("HEAD"))
	private void lifepath$smithingOutput(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onForgeOutput(player, stack,
				io.github.durdeuvlad.lifepath.LifepathMod.id("anvil"));
	}
}
