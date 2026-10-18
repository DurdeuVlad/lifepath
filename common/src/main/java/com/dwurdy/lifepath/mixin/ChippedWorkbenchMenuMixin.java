package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.builder.BuilderStationTakeSupport;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chipped workstation crafts → {@code lifepath:crafting} activity with the
 * {@code lifepath:masonry} marker (NeoForge-only, gated by the
 * {@code lifepath.compat.chipped.mixins.json} config's
 * {@code requiredMods}). Verified against Chipped 4.0.2 bytecode:
 * {@code WorkbenchMenu.craft(ItemStack chosen, boolean bulk)} is the single
 * attended craft entry point for every station type (mason table et al.) —
 * it validates the chosen output against the result list, consumes the
 * material and writes the product into player-inventory slots in place,
 * bypassing {@code ResultSlot.onTake} and never firing a craft event, so
 * neither LifePath's vanilla result-slot hook nor KubeJS's
 * {@code ItemEvents.crafted} can observe it.
 *
 * <p>The hook fires at HEAD on the server thread with the validated
 * output template; the owning player is resolved from the menu's
 * player-inventory slots in the support class (public API only).
 */
@Pseudo
@Mixin(targets = "earth.terrarium.chipped.common.menus.WorkbenchMenu",
		remap = false)
public abstract class ChippedWorkbenchMenuMixin {

	@Inject(method = "craft", at = @At("HEAD"), remap = false, require = 0)
	private void lifepath$chippedCraft(ItemStack output, boolean bulk,
			CallbackInfo ci) {
		BuilderStationTakeSupport.emitStationCraft(
				(AbstractContainerMenu) (Object) this, output, "masonry");
	}
}
