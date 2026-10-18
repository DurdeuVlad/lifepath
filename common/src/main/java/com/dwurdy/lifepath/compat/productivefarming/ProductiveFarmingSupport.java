package com.dwurdy.lifepath.compat.productivefarming;

import com.dwurdy.lifepath.LifepathMod;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Reflective gates for Productive Farming's NeoForge event handlers
 * ({@code EntityInteract} / {@code UseItemOnBlockEvent}). All member access
 * is reflective so neither the foreign event type nor NeoForge's hierarchy
 * appears in signatures — same convention as {@code compat.firstaid}.
 */
public final class ProductiveFarmingSupport {

	private ProductiveFarmingSupport() {
	}

	/**
	 * {@code event.isCanceled() && event.getCancellationResult().consumesAction()}
	 * — the foreign handler preempted the vanilla interaction with a real
	 * consume (wolf armor equip/shear paths cancel with sidedSuccess).
	 */
	public static boolean cancelledAndConsumed(Object event) {
		try {
			Object canceled = event.getClass().getMethod("isCanceled").invoke(event);
			if (!Boolean.TRUE.equals(canceled)) {
				return false;
			}
			Object result = event.getClass()
					.getMethod("getCancellationResult").invoke(event);
			return result instanceof InteractionResult ir && ir.consumesAction();
		} catch (ReflectiveOperationException e) {
			debug(e, event);
			return false;
		}
	}

	/** {@code event.getEntity()} — the interacting player. */
	@Nullable
	public static Player eventPlayer(Object event) {
		try {
			Object player = event.getClass().getMethod("getEntity").invoke(event);
			return player instanceof Player p ? p : null;
		} catch (ReflectiveOperationException e) {
			debug(e, event);
			return null;
		}
	}

	/** {@code event.getTarget()} — the interacted entity. */
	@Nullable
	public static LivingEntity eventTarget(Object event) {
		try {
			Object target = event.getClass().getMethod("getTarget").invoke(event);
			return target instanceof LivingEntity e ? e : null;
		} catch (ReflectiveOperationException e) {
			debug(e, event);
			return null;
		}
	}

	private static void debug(Exception e, Object event) {
		LifepathMod.LOGGER.debug("PF support: reflective read on {} failed: {}",
				event == null ? "null" : event.getClass().getName(), e.toString());
	}
}
