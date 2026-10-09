package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.skill.MaterialGates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla-leak closure for material gates: the crafting table and the
 * inventory 2x2 grid both funnel result computation through {@code
 * slotChangedCraftingGrid}, so clearing the result here blocks every take
 * path (click, shift-click, pick-all) before it can exist. Overgeared's
 * recipe wipe already kills vanilla gear recipes on NeoForge, but assembly
 * recipes and untouched utilities (flint and steel) still produce tier-tagged
 * output — and on Fabric nothing covers them at all. No blueprint slot exists
 * on a vanilla grid; licensed work only happens at the Overgeared anvil.
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuGateMixin {

	@Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
	private static void lifepath$gateCraftingResult(AbstractContainerMenu menu,
			Level level, Player player, CraftingContainer craftSlots,
			ResultContainer resultSlots, RecipeHolder<CraftingRecipe> recipe,
			CallbackInfo ci) {
		if (!(player instanceof ServerPlayer sp)) {
			return;
		}
		var denial = MaterialGates.denialFor(sp, resultSlots.getItem(0), false);
		if (denial != null) {
			resultSlots.setItem(0, ItemStack.EMPTY);
			MaterialGates.notifyDenied(sp, denial);
		}
	}
}
