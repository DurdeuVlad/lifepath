package com.dwurdy.lifepath.compat.hearthandharvest;

import com.dwurdy.lifepath.LifepathMod;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Reflective gates for Hearth &amp; Harvest's NeoForge event handlers
 * ({@code EntityInteract} subclass of {@code PlayerInteractEvent}). All
 * member access is reflective so neither the foreign event type nor
 * NeoForge's hierarchy appears in signatures — the same convention as
 * {@code compat.firstaid.FirstAidMedical}.
 */
public final class HearthHarvestSupport {

	private HearthHarvestSupport() {
	}

	/**
	 * {@code event.isCanceled() && event.getCancellationResult().consumesAction()}
	 * — true exactly when the foreign handler preempted the vanilla
	 * interaction <em>with a successful consume</em>. The FAIL-cancel path
	 * (ChickenPlucking's cooldown rejection sets a FAIL result) reads false,
	 * so rejected interacts never pay.
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
		LifepathMod.LOGGER.debug("HH support: reflective read on {} failed: {}",
				event == null ? "null" : event.getClass().getName(), e.toString());
	}
}
