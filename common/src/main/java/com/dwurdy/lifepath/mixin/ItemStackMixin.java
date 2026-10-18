package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item-side entity-interaction funnel (husbandry {@code lifepath:cared}).
 * {@code ItemStack.interactLivingEntity} is where item-implemented animal
 * interactions land — name tags, saddles, leads, and Animal Husbandry's
 * care items (brush, medicine, fertility potion, magnifying glass) — and it
 * only runs when the entity-side {@code mobInteract} didn't consume, so it
 * can never double-emit against the feed/milk seams. Shears are the one
 * item-side exception — the {@code ShearsItemMixin} emit lives inside this
 * same funnel, so the producer skips them explicitly. A consumed result on
 * an Animal = one care action.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

	@Inject(method = "interactLivingEntity", at = @At("TAIL"))
	private void lifepath$caredOnEntity(Player player, LivingEntity target,
			InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		HusbandryProducers.onItemInteractOnEntity(player, target,
				(ItemStack) (Object) this,
				cir.getReturnValue(), cir.isCancelled());
	}
}
