package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Farmer's Delight cooking-pot takes → Cooking XP (NeoForge-only, gated by
 * the {@code lifepath.compat.farmersdelight.mixins.json} config's
 * {@code requiredMods}). Verified against FD 1.2.9 bytecode:
 * {@code CookingPotResultSlot} is a custom {@code SlotItemHandler} whose
 * {@code onTake} mirrors vanilla's achievement bookkeeping but never posts
 * {@code ItemCraftedEvent}, so pot output earns nothing without this hook.
 * Attributed to whoever takes the meal.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot",
		remap = false)
public abstract class FarmersDelightCookingPotMixin {

	@Inject(method = "onTake", at = @At("TAIL"), remap = false, require = 0)
	private void lifepath$cookingPotTake(Player player, ItemStack stack, CallbackInfo ci) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
