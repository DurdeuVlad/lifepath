package com.dwurdy.lifepath.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.species.DietService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * M15-2/D5 diet hard-block: wraps the {@code canEat} check inside
 * {@link Item#use} — a diet-denied food can no longer START eating, so the
 * item is never consumed (vs. the pre-Beta-8 zero-nutrition eat that looked
 * like eating "worked"). Server-side only; the client still predicts the use
 * once, but {@code ServerPlayerGameMode.useItem} resyncs the inventory
 * ({@code InventoryMenu.sendAllDataToRemote}) so no ghost stack survives —
 * and the denial actionbar explains it. Instant-use foods that bypass
 * {@code canEat} are covered by {@link ItemUtilsMixin}; direct
 * {@code Player.eat} calls still hit the zero-nutrition backstop in
 * {@link PlayerEntityMixin}.
 */
@Mixin(Item.class)
public abstract class ItemMixin {

	@WrapOperation(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;canEat(Z)Z"))
	private boolean lifepath$dietUseGate(Player player, boolean canAlwaysEat,
			Operation<Boolean> original, Level level, Player usingPlayer,
			InteractionHand hand) {
		if (player instanceof ServerPlayer sp && !DietService.allows(
				CharacterManager.getCharacter(sp), sp.getItemInHand(hand))) {
			DietService.denyFood(sp);
			return false;
		}
		return original.call(player, canAlwaysEat);
	}
}
