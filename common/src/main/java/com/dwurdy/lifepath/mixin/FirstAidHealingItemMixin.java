package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.compat.firstaid.FirstAidMedical;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Treatment XP seam for First Aid (New): injects into the funnel every
 * {@code ItemHealing} right-click passes through —
 * {@code HealingItemApiHelperImpl.onItemRightClick}. One hook covers bandage,
 * plaster, morphine, painkillers, adrenaline and the injectors. Emits only
 * when the interaction consumed (healing actually applied). Target class is a
 * string + {@link Pseudo} — never loads when firstaid is absent.
 */
@Pseudo
@Mixin(targets = "ichttt.mods.firstaid.common.apiimpl.HealingItemApiHelperImpl",
		remap = false)
public abstract class FirstAidHealingItemMixin {

	@Inject(method = "onItemRightClick(Lichttt/mods/firstaid/api/healing/ItemHealing;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
			at = @At("RETURN"), remap = false, require = 0)
	private void lifepath$onHealingUse(@Coerce Object itemHealing, Level level,
			Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
		try {
			if (cir.getReturnValue().getResult().consumesAction()) {
				FirstAidMedical.emitHealingUse(player, hand);
			}
		} catch (Throwable t) {
			com.dwurdy.lifepath.LifepathMod.LOGGER.debug(
					"firstaid healing seam failed", t);
		}
	}
}
