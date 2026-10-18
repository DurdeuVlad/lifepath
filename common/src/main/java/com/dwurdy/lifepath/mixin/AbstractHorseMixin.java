package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Horse-family feeding seam (husbandry): in 1.21.1 {@code Horse} and
 * {@code AbstractChestedHorse} handle food inside their own
 * {@code mobInteract} via {@link AbstractHorse#fedFood} — the call never
 * reaches {@code Animal.mobInteract}, so the generic animal seam misses it.
 * A TAIL on {@code fedFood} covers every subclass caller (horse, donkey,
 * mule, llama, trader llama) exactly once; a consumed result = fed.
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {

	@Inject(method = "fedFood", at = @At("TAIL"))
	private void lifepath$horseFed(Player player, ItemStack stack,
			CallbackInfoReturnable<InteractionResult> cir) {
		HusbandryProducers.onHorseFedFood(player, (AbstractHorse) (Object) this,
				cir.getReturnValue());
	}
}
