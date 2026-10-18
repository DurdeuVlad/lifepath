package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Animal Husbandry 0.4.2 compat seam (NeoForge-only, gated by
 * {@code lifepath.compat.animalhusbandry.mixins.json}'s
 * {@code requiredMods}). AH routes all managed-species gameplay through its
 * own mixin handlers on vanilla {@code Animal} methods — the same methods we
 * hook — so lifepath mixes into AH's handler class itself (a mixin on a
 * mixin; the merged class IS the Animal at runtime).
 *
 * <ul>
 *   <li>{@code animalhusbandry$handleInteractions} RETURN — AH's
 *       HEAD-cancellable feed/domesticate path preempts
 *       {@code Animal.mobInteract} for managed species (cow/sheep/pig/goat/
 *       chicken + {@code animalhusbandry:*}), so the base TAIL never sees it.
 *       A consumed return value on a food item = {@code lifepath:fed}.</li>
 *   <li>{@code animalhusbandry$startPregnancy} RETURN — AH cancels
 *       {@code spawnChildFromBreeding} for managed species (pregnancy
 *       replaces instant birth; {@code BabyEntitySpawnEvent} never posts).
 *       Whether our base RETURN-inject on that method sees the injected
 *       cancel-return depends on mixin application order — this seam emits
 *       {@code lifepath:bred} for the cancelled path regardless, and the base
 *       emit skips cancelled calls, so conception pays exactly once.</li>
 *   <li>{@code onAiStep} → wraps {@code addFreshEntityWithPassengers} —
 *       the deferred birth when pregnancy completes. Emits
 *       {@code lifepath:born} attributed to the conception credit recorded
 *       on the mother.</li>
 * </ul>
 *
 * AH stores no breeder on the child (and its auto-breeding calls
 * {@code setInLove(null)}), so attribution runs on lifepath's own
 * {@code HusbandryTracked} record — {@code PregnancyManager.getCausedByPlayer}
 * does not exist in 0.4.2.
 */
@Pseudo
@Mixin(targets = "org.athisworkshop.animalhusbandry.mixin.AnimalMixin", remap = false)
public abstract class AnimalHusbandryCompatMixin {

	@Inject(method = "animalhusbandry$handleInteractions", at = @At("RETURN"),
			remap = false, require = 0)
	private void lifepath$ahFeed(Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> mobInteractCir, CallbackInfo ci) {
		ItemStack held = player.getItemInHand(hand);
		HusbandryProducers.onAnimalHusbandryFeed(player,
				(Animal) (Object) this, held, mobInteractCir.getReturnValue());
	}

	@Inject(method = "animalhusbandry$startPregnancy", at = @At("RETURN"),
			remap = false, require = 0)
	private void lifepath$ahConceived(ServerLevel level, Animal partner,
			CallbackInfo spawnCi, CallbackInfo ci) {
		HusbandryProducers.onAnimalHusbandryConception(
				(Animal) (Object) this, partner, spawnCi.isCancelled());
	}

	@WrapOperation(method = "onAiStep",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"),
			remap = false, require = 0)
	private void lifepath$ahBirth(ServerLevel level, Entity child,
			Operation<Void> original, CallbackInfo ci) {
		original.call(level, child);
		HusbandryProducers.onAnimalHusbandryBirth((LivingEntity) (Object) this, child);
	}
}
