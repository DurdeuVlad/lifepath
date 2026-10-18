package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Trotting Wagons hitch seam (LP-INT-2), gated by
 * {@code lifepath.compat.trotting_wagons.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code AbstractWagon.interact → tryHitchHorse → hitchHorse(Mob, Side)}
 * is the single funnel for hitching a draft animal to a wagon — fired for
 * both the click-the-wagon path and the
 * {@code CommonEvents.onEntityInteract} click-the-horse path. One wrapped
 * call = one animal hitched (multi-horse wagons emit per animal) →
 * {@code lifepath:cared} on the hitched mob. The {@code Side} enum is
 * foreign → {@code Object}.
 */
@Pseudo
@Mixin(targets = "net.favouriteless.trotting_wagons.common.entities.base.AbstractWagon",
		remap = false)
public abstract class TrottingWagonsHitchMixin {

	@WrapOperation(method = "tryHitchHorse",
			at = @At(value = "INVOKE",
					target = "Lnet/favouriteless/trotting_wagons/common/entities/base/AbstractWagon;hitchHorse(Lnet/minecraft/world/entity/Mob;Lnet/favouriteless/trotting_wagons/common/entities/base/AbstractWagon$Side;)V"),
			require = 0)
	private void lifepath$twHitched(@Coerce Object wagon, Mob mob, @Coerce Object side,
			Operation<Void> original, Player player) {
		original.call(wagon, mob, side);
		HusbandryProducers.onMobCared(player, mob);
	}
}
