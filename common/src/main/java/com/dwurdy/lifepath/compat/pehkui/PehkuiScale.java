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
	@Nullable private static Object jumpType;
	@Nullable private static Object stepType;

	/**
	 * Sets the player's base scale plus traversal compensation: a shrunk
	 * species (scale &lt; 1) gets {@code pehkui:jump_height} and
	 * {@code pehkui:step_height} raised by {@code 1/scale} so they keep
	 * vanilla jump/step clearance — a 0.7 dwarf otherwise can't jump a
	 * full block. Scales ≥ 1 write 1.0, which also repairs stale values
	 * after a species swap. No-op when Pehkui is absent or cut.
	 */
	public static void setBaseScale(Player player, float scale) {
		if (!resolve()) {
			return;
		}
		try {
			apply(baseType, player, scale);
			float comp = scale < 1.0f ? 1.0f / scale : 1.0f;
			// Optional types: absent fields on older Pehkui leave them null
			// — base scale alone still applies.
			if (jumpType != null) {
				apply(jumpType, player, comp);
			}
			if (stepType != null) {
				apply(stepType, player, comp);
			}
		} catch (Throwable t) {
			cutOnce(t);
		}
	}

	private static void apply(Object type, Player player, float value)
			throws Throwable {
		Object data = getScaleData.invoke(type, (Entity) player);
		setPersistence.invoke(data, Boolean.TRUE);
		setScale.invoke(data, value);
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
				// JUMP_HEIGHT/STEP_HEIGHT are nice-to-have — an older Pehkui
				// lacking them degrades to base-scale-only, never a cut.
				try {
					jumpType = scaleTypes.getField("JUMP_HEIGHT").get(null);
					stepType = scaleTypes.getField("STEP_HEIGHT").get(null);
				} catch (Throwable ignored) {
					jumpType = null;
					stepType = null;
				}
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
