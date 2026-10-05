package com.dwurdy.lifepath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.dwurdy.lifepath.ability.AbilityEngine;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.species.DietService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M5-4 diet backstop: wraps the {@code HungerManager.eat} call inside
 * {@link Player#eat}. The D5 hard-block at the use-start gates
 * ({@code ItemMixin}/{@code ItemUtilsMixin}) keeps denied food from being
 * consumed at all; this wrap remains for direct {@code Player.eat} calls
 * that bypass item use — a denied food still completes the eat but
 * contributes zero hunger/saturation. Client passes through unchanged.
 */
@Mixin(Player.class)
public abstract class PlayerEntityMixin {

	/**
	 * M-3 (morph) lethal-damage seam: {@code Player.actuallyHurt} overrides
	 * {@code LivingEntity}'s, so the interception must live on THIS override —
	 * the {@code setHealth} write it makes is post-armor/post-absorption, i.e.
	 * the true lethal quantity. A write that would kill a morphed player is
	 * rewritten to the carried remainder after force-demorph.
	 */
	@WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"))
	private void lifepath$morphLethalWrite(Player self, float health,
			Operation<Void> original) {
		original.call(self, com.dwurdy.lifepath.morph.MorphService
				.onLethalHealthWrite(self, health));
	}

	/**
	 * M14-3 {@code damage_dealt} seam: wraps the {@code hurt} calls inside
	 * {@link Player#attack} — the main hit ({@code Entity.hurt}) and each
	 * sweep victim ({@code LivingEntity.hurt}). The hook runs only after a
	 * landed hit (hurt returned true): whiffs, invulnerable targets, and
	 * fully-negated hits proc nothing — a shield-ABSORBED hit still lands
	 * and procs. Server-side only.
	 */
	@WrapOperation(method = "attack", at = {
			@At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
			@At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")})
	private boolean lifepath$damageDealtAfterHit(net.minecraft.world.entity.Entity victim,
			net.minecraft.world.damagesource.DamageSource source, float amount,
			Operation<Boolean> original) {
		boolean landed = original.call(victim, source, amount);
		if (landed && (Object) this instanceof ServerPlayer player) {
			AbilityEngine.onDamageDealt(player, victim, source, amount);
		}
		return landed;
	}

	@WrapOperation(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void lifepath$dietGate(net.minecraft.world.food.FoodData manager,
			FoodProperties food, Operation<Void> original,
			Level world, ItemStack stack, FoodProperties foodComponent) {
		if ((Object) this instanceof ServerPlayer player) {
			// M9-1: item-type condition cures/acquisition resolve on the eaten
			// stack regardless of whether the diet gate passes nutrition.
			com.dwurdy.lifepath.condition.ConditionService
					.onItemEaten(player, stack, System.currentTimeMillis());
		}
		if (!((Object) this instanceof ServerPlayer player)
				|| DietService.allows(CharacterManager.getCharacter(player), stack)) {
			original.call(manager, food);
		} else {
			// Backstop path (direct eat calls): denied food yields nothing —
			// tell the player instead of silently looking like it "worked".
			DietService.denyFood(player);
		}
	}
}
