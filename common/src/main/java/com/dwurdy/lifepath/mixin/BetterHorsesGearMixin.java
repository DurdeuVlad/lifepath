package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Icy's Better Horses gear-equip seam (LP-INT-2), gated by
 * {@code lifepath.compat.icys_better_horses.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>IBH's own {@code AbstractHorseMixin} HEAD-intercepts
 * {@code mobInteract} and routes gear equipping (saddles, barding, harness)
 * through the static {@code BhHorseInteraction.equipGearFromHand} helper —
 * a consumed return there means the horse's own {@code mobInteract} never
 * delegates to {@code Animal.mobInteract}, so the vanilla seams stay blind.
 * Equipping gear on a horse is animal handling → {@code lifepath:cared}.
 * The {@code IHorseData} parameter is foreign → {@code Object}.
 */
@Pseudo
@Mixin(targets = "icy.betterhorses.net.BhHorseInteraction", remap = false)
public abstract class BetterHorsesGearMixin {

	@Inject(method = "equipGearFromHand", at = @At("RETURN"), require = 0)
	private static void lifepath$bhGearEquipped(AbstractHorse horse,
			@Coerce Object data, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue().consumesAction()) {
			HusbandryProducers.onMobCared(player, horse);
		}
	}
}
