package io.github.durdeuvlad.lifepath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.species.DietService;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M5-4 diet seam: wraps the {@code HungerManager.eat} call inside
 * {@link PlayerEntity#eatFood}. A diet-denied food still completes the eat
 * (animation, stack consumption, vanilla food side-effects via
 * {@code LivingEntity.eatFood}) but contributes zero hunger/saturation —
 * exactly the spec's "zero nutrition". Client passes through unchanged.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

	@WrapOperation(method = "eatFood", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/player/HungerManager;eat(Lnet/minecraft/component/type/FoodComponent;)V"))
	private void lifepath$dietGate(net.minecraft.entity.player.HungerManager manager,
			FoodComponent food, Operation<Void> original,
			World world, ItemStack stack, FoodComponent foodComponent) {
		if (!((Object) this instanceof ServerPlayerEntity player)
				|| DietService.allows(CharacterManager.getCharacter(player), stack)) {
			original.call(manager, food);
		}
	}
}
