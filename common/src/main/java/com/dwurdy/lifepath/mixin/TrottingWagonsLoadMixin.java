package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.producer.HusbandryProducers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * Trotting Wagons event-side load/unload seams (LP-INT-2), gated by
 * {@code lifepath.compat.trotting_wagons.mixins.json}'s
 * {@code requiredMods}.
 *
 * <p>{@code CommonEvents.onEntityInteract} owns the mob-side half of wagon
 * handling — none of it passes through {@code AbstractWagon.tryMountMob}:
 * <ul>
 *   <li>sneak-click a mob beside a stationary wagon →
 *       {@code lambda$onEntityInteract$1 → entity.startRiding(wagon)}
 *       loads it aboard → {@code lifepath:cared} on the loaded mob;</li>
 *   <li>sneak-click a mob already riding a wagon →
 *       {@code entity.stopRiding()} unloads it → {@code lifepath:cared} on
 *       the unloaded mob.</li>
 * </ul>
 * The wagon-side equivalents live in {@link TrottingWagonsMountMixin}.
 * The {@code EntityInteract} and {@code AbstractWagon} params are foreign
 * → {@link Coerce}.
 */
@Pseudo
@Mixin(targets = "net.favouriteless.trotting_wagons.common.CommonEvents",
		remap = false)
public abstract class TrottingWagonsLoadMixin {

	/** Sneak-click load: mob → wagon seat. */
	@WrapOperation(method = "lambda$onEntityInteract$1",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;startRiding(Lnet/minecraft/world/entity/Entity;)Z"),
			require = 0)
	private static boolean lifepath$twLoadedEntity(Entity rider, Entity wagon,
			Operation<Boolean> original, Level level, Entity entity,
			@Coerce Object event, @Coerce Object wagonArg) {
		boolean result = original.call(rider, wagon);
		if (result && rider instanceof LivingEntity mob) {
			emit(event, mob);
		}
		return result;
	}

	/** Sneak-click unload: riding mob leaves the wagon. */
	@WrapOperation(method = "onEntityInteract",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;stopRiding()V"),
			require = 0)
	private static void lifepath$twUnloadedEntity(Entity rider,
			Operation<Void> original, @Coerce Object event) {
		original.call(rider);
		if (rider instanceof LivingEntity mob) {
			emit(event, mob);
		}
	}

	private static void emit(Object event, LivingEntity mob) {
		try {
			Object player = event.getClass().getMethod("getEntity").invoke(event);
			if (player instanceof Player p) {
				HusbandryProducers.onMobCared(p, mob);
			}
		} catch (ReflectiveOperationException ignored) {
		}
	}
}
