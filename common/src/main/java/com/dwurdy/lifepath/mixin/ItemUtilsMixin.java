package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.species.DietService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Instant-use path ({@code ItemUtils.startUsingInstantly}) — honey bottles
 * and any food item that starts using WITHOUT consulting
 * {@code Player.canEat}: a diet-denied FOOD stack returns {@code fail}
 * before {@code startUsingItem} runs, so it can never be consumed. Non-food
 * items (potions, milk, bows) pass through untouched — a diet governs
 * nourishment, not item use in general.
 */
@Mixin(ItemUtils.class)
public abstract class ItemUtilsMixin {

	@Inject(method = "startUsingInstantly", at = @At("HEAD"), cancellable = true)
	private static void lifepath$dietInstantGate(Level level, Player player,
			InteractionHand hand,
			CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer sp && stack.has(DataComponents.FOOD)
				&& !DietService.allows(CharacterManager.getCharacter(sp), stack)) {
			DietService.denyFood(sp);
			cir.setReturnValue(InteractionResultHolder.fail(stack));
		}
	}
}
