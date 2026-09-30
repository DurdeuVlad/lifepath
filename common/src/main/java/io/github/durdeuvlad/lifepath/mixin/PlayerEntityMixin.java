package io.github.durdeuvlad.lifepath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.species.DietService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M5-4 diet seam: wraps the {@code HungerManager.eat} call inside
 * {@link Player#eat}. A diet-denied food still completes the eat
 * (animation, stack consumption, vanilla food side-effects via
 * {@code LivingEntity.eatFood}) but contributes zero hunger/saturation —
 * exactly the spec's "zero nutrition". Client passes through unchanged.
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
		original.call(self, io.github.durdeuvlad.lifepath.morph.MorphService
				.onLethalHealthWrite(self, health));
	}

	@WrapOperation(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void lifepath$dietGate(net.minecraft.world.food.FoodData manager,
			FoodProperties food, Operation<Void> original,
			Level world, ItemStack stack, FoodProperties foodComponent) {
		if ((Object) this instanceof ServerPlayer player) {
			// M9-1: item-type condition cures/acquisition resolve on the eaten
			// stack regardless of whether the diet gate passes nutrition.
			io.github.durdeuvlad.lifepath.condition.ConditionService
					.onItemEaten(player, stack, System.currentTimeMillis());
		}
		if (!((Object) this instanceof ServerPlayer player)
				|| DietService.allows(CharacterManager.getCharacter(player), stack)) {
			original.call(manager, food);
		}
	}
}
