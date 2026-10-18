package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shearing seam (husbandry): NeoForge replaces the per-animal
 * {@code mobInteract} shear branches with the {@code IShearable} pipeline —
 * shears→entity interactions resolve through
 * {@code ShearsItem.interactLivingEntity} which invokes
 * {@code IShearable.onSheared}. Emitting here covers every shearable animal
 * (sheep, mooshroom, and modded {@code IShearable}s) in one seam. Dispenser
 * shearing ({@code ShearsDispenseItemBehavior}) never reaches this method, so
 * unattended shearing pays nothing. Non-{@code Animal} targets (snow golem
 * pumpkin) are ignored — not livestock husbandry.
 */
@Mixin(ShearsItem.class)
public abstract class ShearsItemMixin {

	@Inject(method = "interactLivingEntity", at = @At("TAIL"), require = 0)
	private void lifepath$onSheared(ItemStack stack, Player player,
			LivingEntity target, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction() && target instanceof Animal animal) {
			HusbandryProducers.onSheared(player, animal);
		}
	}
}
