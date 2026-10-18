package com.dwurdy.lifepath.compat.icysbetterhorses;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Reflective helpers for Icy's Better Horses. All foreign member access is
 * reflective so IBH internals never appear in signatures — the same
 * convention as {@code compat.firstaid} / {@code compat.kaleidoscope}.
 */
public final class BetterHorsesSupport {

	private BetterHorsesSupport() {
	}

	/**
	 * {@code outcome.ok()} on {@code HorseManagement}'s {@code Outcome}
	 * record — true exactly when the roster command resolved.
	 */
	public static boolean outcomeOk(@Nullable Object outcome) {
		if (outcome == null) {
			return false;
		}
		try {
			Object ok = outcome.getClass().getMethod("ok").invoke(outcome);
			return Boolean.TRUE.equals(ok);
		} catch (ReflectiveOperationException e) {
			debug(e, outcome);
			return false;
		}
	}

	/**
	 * {@code cart.boundHorse()} — the cart's bound horse as a plain entity,
	 * or {@code null} while unbound. {@code interactAt} fires for chest/
	 * plough/bench interactions, all of which are acts on the hitched
	 * animal's rig.
	 */
	@Nullable
	public static LivingEntity boundHorse(Object cart) {
		try {
			Object horse = cart.getClass().getMethod("boundHorse").invoke(cart);
			return horse instanceof LivingEntity e ? e : null;
		} catch (ReflectiveOperationException e) {
			debug(e, cart);
			return null;
		}
	}

	/**
	 * Horse-roster management emit (whistle / send-home / set-active /
	 * disown). The horse is identified by UUID and may live unloaded in
	 * storage, so there is no entity to pull tags from — the subject is the
	 * generic {@code minecraft:horse} and the tag set mirrors what the real
	 * entity carries ({@code lifepath:animals} + the {@code cared} marker).
	 */
	public static void emitHorseManaged(Player player) {
		if (!(player instanceof ServerPlayer sp) || isAutomation(sp)) {
			return;
		}
		ActivityDispatcher.publish(new ActivityEvent(sp, ActivityTypes.HUSBANDRY,
				ResourceLocation.withDefaultNamespace("horse"),
				Set.of(LifepathMod.id("animals"), LifepathMod.id("cared")),
				ActivityEvent.Cause.PLAYER, System.currentTimeMillis(),
				Map.of("action", "cared")));
	}

	private static boolean isAutomation(Player player) {
		return com.dwurdy.lifepath.platform.Platform.isInitialized()
				&& com.dwurdy.lifepath.platform.Platform.get().isAutomation(player);
	}

	private static void debug(Exception e, Object target) {
		LifepathMod.LOGGER.debug("IBH support: reflective read on {} failed: {}",
				target == null ? "null" : target.getClass().getName(), e.toString());
	}
}
