package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.woodworks.WoodworksSawmill;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Woodworks sawmill take-output XP seam (NeoForge-only, gated by the {@code
 * lifepath.compat.woodworks.mixins.json} config's {@code requiredMods}).
 * Verified against woodworks 4.0.2 bytecode: {@code SawmillMenu$2} is the
 * result slot — its {@code onTake(Player, ItemStack)} mirrors vanilla's
 * stonecutter result slot (consumes input, awards used recipes, plays the
 * saw sound). {@code quickMoveStack} reaches the same method through the
 * polymorphic {@code Slot.onTake} call, so normal and shift-click takes both
 * land here. Attributed to the taker.
 */
@Pseudo
@Mixin(targets = "com.teamabnormals.woodworks.common.inventory.SawmillMenu$2",
		remap = false)
public abstract class WoodworksSawmillMixin {

	@Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$onSawmillTake(Player player, ItemStack stack,
			CallbackInfo ci) {
		try {
			WoodworksSawmill.onSawmillTake(player, stack);
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"woodworks sawmill seam failed", t);
		}
	}
}
