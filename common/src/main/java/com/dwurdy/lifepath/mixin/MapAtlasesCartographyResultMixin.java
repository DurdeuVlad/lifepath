package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.mapatlases.MapAtlasesCartography;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scholarship seam for Map Atlases' cartography-table work. map_atlases
 * fills the <em>vanilla</em> cartography result slot — the anonymous
 * {@code CartographyTableMenu$5} — with the updated atlas (atlas + empty map)
 * or extracted filled maps (atlas + shears / atlas + filled map); taking it
 * is the production moment. Vanilla cartography output (cloned, scaled,
 * locked maps) is the same take-out and pays the same way once tagged.
 * Covers click and shift-click — {@code quickMoveStack} routes the result
 * slot through {@code onTake} too. A vanilla-target mixin living in the
 * map_atlases config because the atlas workflows are what make this slot
 * worth paying for; promoting it to {@code lifepath.mixins.json} would cover
 * vanilla-only cartography.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$5")
public abstract class MapAtlasesCartographyResultMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), require = 0)
	private void lifepath$cartographyOutput(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			MapAtlasesCartography.emitTake(player, stack);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug("map_atlases cartography seam failed", t);
		}
	}
}
