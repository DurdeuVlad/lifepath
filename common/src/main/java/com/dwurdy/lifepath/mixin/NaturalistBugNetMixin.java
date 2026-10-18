package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Naturalist bug-net seam (LP-INT-2), gated by
 * {@code lifepath.compat.naturalist.mixins.json}'s {@code requiredMods}.
 *
 * <p>{@code BugNetItem.interactLivingEntity} runs inside the item-side
 * {@code ItemStack.interactLivingEntity} funnel, which already emits
 * {@code lifepath:cared} for {@code Animal} targets (butterfly, firefly,
 * caterpillar). This emit is the complement: only non-{@code Animal}
 * catches (the {@code PathfinderMob} dragonfly) escape the base funnel, so
 * the Animal check below is what keeps this seam from double-paying.
 */
@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.server.item.BugNetItem",
		remap = false)
public abstract class NaturalistBugNetMixin {

	@Inject(method = "interactLivingEntity", at = @At("TAIL"), require = 0)
	private void lifepath$natNetCaught(ItemStack stack, Player player,
			LivingEntity target, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction() || target instanceof Animal) {
			return;
		}
		HusbandryProducers.onMobCared(player, target);
	}
}
