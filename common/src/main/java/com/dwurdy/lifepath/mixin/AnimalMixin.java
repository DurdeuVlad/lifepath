package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.dwurdy.lifepath.producer.HusbandryTracked;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Husbandry seam on {@link Animal} (M-ticket): feed events, breeding
 * resolution, instant vanilla birth, and the on-entity attribution record.
 *
 * <ul>
 *   <li>{@code mobInteract} TAIL → {@code lifepath:fed} (or {@code cared} for
 *       a consumed non-food result). {@code cir.isCancelled()} skips the
 *       emit — HEAD-cancelling mods (Animal Husbandry's managed-species
 *       handler) report through the compat seam instead, never twice.</li>
 *   <li>{@code spawnChildFromBreeding} RETURN → {@code lifepath:bred} when
 *       the pairing resolved (both parents on the breed cooldown after the
 *       call). The credit stamps {@code birthCredit} on BOTH parents — under
 *       Animal Husbandry's pregnancy path the female delivers days later and
 *       reads it back; vanilla reads it never (instant birth below).</li>
 *   <li>{@code spawnChildFromBreeding} → wraps
 *       {@code ServerLevel.addFreshEntityWithPassengers} →
 *       {@code lifepath:born} on the newborn. AH cancels this method for
 *       managed species, so their birth emits through
 *       {@code AnimalHusbandryCompatMixin} instead.</li>
 *   <li>{@link HusbandryTracked} fields + NBT round-trip — the attribution
 *       record survives chunk unload/restart (AH-length pregnancies).</li>
 * </ul>
 */
@Mixin(Animal.class)
public abstract class AnimalMixin implements HusbandryTracked {

	@Unique
	private UUID lifepath$lastInteractor;
	@Unique
	private long lifepath$lastInteractGameTime;
	@Unique
	private UUID lifepath$birthCredit;

	@Override
	public UUID lifepath$getLastInteractor() {
		return lifepath$lastInteractor;
	}

	@Override
	public void lifepath$setLastInteractor(@Nullable UUID playerId) {
		lifepath$lastInteractor = playerId;
	}

	@Override
	public long lifepath$getLastInteractGameTime() {
		return lifepath$lastInteractGameTime;
	}

	@Override
	public void lifepath$setLastInteractGameTime(long gameTime) {
		lifepath$lastInteractGameTime = gameTime;
	}

	@Override
	public UUID lifepath$getBirthCredit() {
		return lifepath$birthCredit;
	}

	@Override
	public void lifepath$setBirthCredit(@Nullable UUID playerId) {
		lifepath$birthCredit = playerId;
	}

	@Inject(method = "mobInteract", at = @At("TAIL"))
	private void lifepath$husbandryInteract(Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		HusbandryProducers.onAnimalMobInteract(player, (Animal) (Object) this, hand,
				cir.getReturnValue(), cir.isCancelled());
	}

	@Inject(method = "spawnChildFromBreeding", at = @At("RETURN"))
	private void lifepath$breedingResolved(ServerLevel level, Animal partner,
			CallbackInfo ci) {
		HusbandryProducers.onBreedingResolved((Animal) (Object) this, partner,
				ci.isCancelled());
	}

	@WrapOperation(method = "spawnChildFromBreeding",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
	private void lifepath$onBirthSpawn(ServerLevel level, Entity child,
			Operation<Void> original, ServerLevel enclosingLevel, Animal partner) {
		original.call(level, child);
		HusbandryProducers.onChildSpawned((Animal) (Object) this, partner, child);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void lifepath$saveHusbandry(CompoundTag tag, CallbackInfo ci) {
		HusbandryProducers.saveTracked((Animal) (Object) this, tag);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void lifepath$loadHusbandry(CompoundTag tag, CallbackInfo ci) {
		HusbandryProducers.loadTracked((Animal) (Object) this, tag);
	}
}
