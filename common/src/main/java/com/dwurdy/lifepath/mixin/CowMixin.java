package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cow milking seam (husbandry): the bucket→milk swap inside
 * {@code Cow.mobInteract} is the only thing that calls
 * {@code ItemUtils.createFilledResult} there — wrapping it emits
 * {@code lifepath:milked} exactly when a bucket of milk is taken, mooshroom
 * bucket-milking included (it delegates to {@code Cow.mobInteract}). A mod
 * that HEAD-cancels milking (AH's daily-milk gate) simply never reaches the
 * invoke. Wheat feeds pass through {@code Animal.mobInteract} untouched by
 * this point.
 */
@Mixin(Cow.class)
public abstract class CowMixin {

	@WrapOperation(method = "mobInteract",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack lifepath$milked(ItemStack held, Player player, ItemStack result,
			Operation<ItemStack> original, Player interactingPlayer, InteractionHand hand) {
		ItemStack filled = original.call(held, player, result);
		HusbandryProducers.onMilkTaken(interactingPlayer, (Animal) (Object) this);
		return filled;
	}
}
