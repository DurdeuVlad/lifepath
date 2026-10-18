package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Goat milking seam (husbandry): mirrors {@link CowMixin} — the
 * {@code createFilledResult} call inside {@code Goat.mobInteract} is the
 * bucket→milk swap. Feeding delegates to {@code Animal.mobInteract}, which
 * emits {@code lifepath:fed} through the generic animal seam.
 */
@Mixin(Goat.class)
public abstract class GoatMixin {

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
