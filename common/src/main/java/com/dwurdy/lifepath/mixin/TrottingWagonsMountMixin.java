package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Trotting Wagons mob-loading seam (LP-INT-2), gated by
 * {@code lifepath.compat.trotting_wagons.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code AbstractWagon.tryMountMob} loads a nearby mob onto the wagon as
 * cargo — the only player-facing path that seats it ({@code interact}
 * delegates here). The {@code Mob.startRiding} invoke only runs once the
 * seat check passed → a true result = one animal loaded →
 * {@code lifepath:cared}.
 */
@Pseudo
@Mixin(targets = "net.favouriteless.trotting_wagons.common.entities.base.AbstractWagon",
		remap = false)
public abstract class TrottingWagonsMountMixin {

	@WrapOperation(method = "tryMountMob",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Mob;startRiding(Lnet/minecraft/world/entity/Entity;)Z"),
			require = 0)
	private boolean lifepath$twLoaded(Mob mob, Entity vehicle,
			Operation<Boolean> original, Player player) {
		boolean result = original.call(mob, vehicle);
		if (result) {
			HusbandryProducers.onMobCared(player, mob);
		}
		return result;
	}
}
