package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.farmersrespite.FarmersRespiteTakeSupport;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Farmers Respite kettle result-slot takes → Brewing XP (NeoForge-only,
 * gated by the {@code lifepath.compat.farmersrespite.mixins.json} config's
 * {@code requiredMods}). Verified against farmersrespite 3.0.1 bytecode:
 * {@code KettleResultSlot.onTake(Player, ItemStack)} is the kettle menu's
 * take-output hook — the same shape as the existing FD {@code
 * CookingPotResultSlot.onTake} seam. {@code KettleContainer}'s anonymous
 * result slot ({@code KettleContainer$4}) overrides {@code onTake} to call
 * {@code clearUsedRecipes} then {@code super.onTake}, so one TAIL inject
 * on the parent method covers click-take and shift-click. Attributed to
 * the taking player; hopper extraction bypasses the slot entirely.
 */
@Pseudo
@Mixin(targets = "com.farmersrespite.common.block.entity.container.KettleResultSlot",
		remap = false)
public abstract class FarmersRespiteKettleSlotMixin {

	@Inject(method = "onTake", at = @At("TAIL"), remap = false, require = 0)
	private void lifepath$emitOnTake(Player player, ItemStack stack,
			CallbackInfo ci) {
		FarmersRespiteTakeSupport.emitBrewedTake(player, stack);
	}
}
