package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.butchery.ButcheryProcessing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Butchery station take-output XP seam (NeoForge-only, gated by the {@code
 * lifepath.compat.butchery.mixins.json} config's {@code requiredMods}).
 * Verified against butchery 5.2 bytecode: the generated GUI menus expose
 * their result slots as anonymous {@code SlotItemHandler} subclasses whose
 * {@code mayPlace} returns literal false — meat grinder output slots
 * {@code MeatgrinderguiMenu$5/$7}, pestle &amp; mortar {@code
 * PestleandmotarguiMenu$3}, taxidermy station {@code
 * TaxidermystationguiMenu$5}.
 *
 * <p>The funnel is {@link Slot#onTake}: {@code SlotItemHandler} does NOT
 * declare {@code onTake} (verified on NeoForge 21.1.248 — it inherits it
 * from {@code Slot}), and only the taxidermy slot overrides it (delegating
 * to super). Mixing into {@code Slot} itself therefore covers every
 * station take — normal click and {@code quickMoveStack} shift-click alike
 * — for all four output slots, while the helper filters on the exact
 * anonymous slot class names: a butchery-side renumber degrades to a miss,
 * never a wrong-payment, and the mixin only applies at all while butchery
 * is present. Attributed to the taker.
 */
@Mixin(Slot.class)
public abstract class ButcheryStationSlotMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("RETURN"), require = 1)
	private void lifepath$onStationTake(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			ButcheryProcessing.onStationTake(this, player, stack);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"butchery station slot seam failed", t);
		}
	}
}
