package com.dwurdy.lifepath.compat.pehkui;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.Platform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Soft-dep write bridge into Pehkui (compat A5): sets a player's
 * {@code pehkui:base} scale for species physical size. Same resilience
 * contract as {@code VampirismFactions} — the Pehkui classes resolve lazily
 * by name on first call; anything missing or mis-signed flips {@link #cut},
 * after which every call is a constant no-op and the failure logs once.
 *
 * <p>{@code ScaleData.setPersistence(true)} is applied alongside the scale
 * so the value survives respawn without relying on Pehkui's global
 * keep-on-respawn config. Reapplying on join/respawn is still the
 * caller's job — the bridge itself is stateless.
 */
public final class PehkuiScale {
	private PehkuiScale() {
	}

	private static final String MOD_ID = "pehkui";
	private static final String SCALE_TYPES = "virtuoel.pehkui.api.ScaleTypes";
	private static final String SCALE_TYPE = "virtuoel.pehkui.api.ScaleType";
	private static final String SCALE_DATA = "virtuoel.pehkui.api.ScaleData";

	private static volatile boolean resolved;
	private static volatile boolean cut;
	@Nullable private static MethodHandle getScaleData;
	@Nullable private static MethodHandle setScale;
	@Nullable private static MethodHandle setPersistence;
	@Nullable private static Object baseType;

	/**
	 * Sets the player's base scale. No-op when Pehkui is absent or the
	 * bridge has cut — a pack without Pehkui pays one mod-presence check.
	 */
	public static void setBaseScale(Player player, float scale) {
		if (!resolve()) {
			return;
		}
		try {
			Object data = getScaleData.invoke(baseType, (Entity) player);
			setPersistence.invoke(data, Boolean.TRUE);
			setScale.invoke(data, scale);
		} catch (Throwable t) {
			cutOnce(t);
		}
	}

	/** One-shot lazy resolution — Pehkui classes never load while absent. */
	private static boolean resolve() {
		if (cut) {
			return false;
		}
		if (!Platform.get().isModLoaded(MOD_ID)) {
			return false;
		}
		if (resolved) {
			return true;
		}
		synchronized (PehkuiScale.class) {
			if (resolved || cut) {
				return !cut;
			}
			try {
				Class<?> scaleTypes = Class.forName(SCALE_TYPES);
				Class<?> scaleType = Class.forName(SCALE_TYPE);
				Class<?> scaleData = Class.forName(SCALE_DATA);
				Field base = scaleTypes.getField("BASE");
				baseType = base.get(null);
				MethodHandles.Lookup lk = MethodHandles.publicLookup();
				getScaleData = lk.findVirtual(scaleType, "getScaleData",
						MethodType.methodType(scaleData, Entity.class));
				setScale = lk.findVirtual(scaleData, "setScale",
						MethodType.methodType(void.class, float.class));
				setPersistence = lk.findVirtual(scaleData, "setPersistence",
						MethodType.methodType(void.class, Boolean.class));
				resolved = true;
				LifepathMod.LOGGER.info(
						"Pehkui scale bridge active — species scale live");
				return true;
			} catch (Throwable t) {
				cutOnce(t);
				return false;
			}
		}
	}

	private static void cutOnce(Throwable t) {
		if (!cut) {
			cut = true;
			LifepathMod.LOGGER.warn(
					"Pehkui scale bridge cut — {} ({}). Species scale is "
							+ "inert for the session.",
					t.getClass().getSimpleName(), t.getMessage());
		}
	}
}
