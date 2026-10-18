package com.dwurdy.lifepath.compat.tobacconist;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Reflective helpers for the Tobacconist processing take-out mixins — the
 * same degrade-to-debug convention as the Kaleidoscope compat layer (common
 * has no tobacconist classes on its compile classpath, so member access is
 * reflective; a mod update that renames internals degrades to a debug log
 * instead of a hard failure).
 */
public final class TobacconistTakeSupport {

	private TobacconistTakeSupport() {
	}

	/**
	 * Crafting-activity take-out for a processed tobacco stack (cured leaf,
	 * fermented/aged barrel tobacco). Emitted through
	 * {@link VanillaGameplayProducers#onCraftOutput} — the same funnel as
	 * cooking-pot and keg takes — so the FakePlayer/automation gate and
	 * outcome rules apply uniformly. xp_source data weights the item ids.
	 */
	public static void emitTake(LivingEntity taker, @Nullable ItemStack stack) {
		if (!(taker instanceof Player player) || stack == null || stack.isEmpty()) {
			return;
		}
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}

	/**
	 * Hanging-bundle break harvest: resolves the bundle block entity through
	 * the block's own {@code getBundleEntity(Level, BlockState, BlockPos)}
	 * (it maps either half of the double block onto the lower BE), then
	 * emits the stored leaf — but only when the bundle actually finished
	 * curing, so breaking a still-drying bundle (which just returns the raw
	 * leaf the player loaded) pays nothing.
	 */
	public static void emitBundleHarvest(Object block, Level level, BlockPos pos,
			BlockState state, Player player) {
		try {
			java.lang.reflect.Method m = findMethod(block.getClass(),
					"getBundleEntity", Level.class, BlockState.class, BlockPos.class);
			Object be = m == null ? null : m.invoke(null, level, state, pos);
			if (be == null || !invokeBool(be, "isFinished")) {
				return;
			}
			emitTake(player, readStack(be, "getStoredLeaf"));
		} catch (ReflectiveOperationException | RuntimeException e) {
			LifepathMod.LOGGER.debug("Tobacconist bundle harvest read failed: {}", e.toString());
		}
	}

	/** No-arg boolean accessor (e.g. {@code isFinished}). False on failure. */
	public static boolean invokeBool(Object target, String method) {
		try {
			java.lang.reflect.Method m = findMethod(target.getClass(), method);
			Object value = m == null ? null : m.invoke(target);
			return Boolean.TRUE.equals(value);
		} catch (ReflectiveOperationException | RuntimeException e) {
			debug(e, target, method);
			return false;
		}
	}

	/**
	 * Static {@code ItemStack}-arg accessor on the target's class — used for
	 * the barrel's {@code isFermented(ItemStack)} / {@code
	 * getAgedDays(ItemStack)} quality markers, which live on the block
	 * entity class but take only the stack.
	 */
	public static Object invokeStatic(Object owner, String method, ItemStack arg) {
		try {
			java.lang.reflect.Method m = findMethod(owner.getClass(), method, ItemStack.class);
			return m == null ? null : m.invoke(null, arg);
		} catch (ReflectiveOperationException | RuntimeException e) {
			debug(e, owner, method);
			return null;
		}
	}

	/**
	 * Calls a no-arg accessor on {@code be} (e.g. {@code getStoredLeaf}) and
	 * returns a defensive copy — same convention as the Kaleidoscope take
	 * support's {@code readStack}.
	 */
	public static ItemStack readStack(Object be, String method) {
		try {
			java.lang.reflect.Method m = findMethod(be.getClass(), method);
			Object value = m == null ? null : m.invoke(be);
			return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
		} catch (ReflectiveOperationException | ClassCastException e) {
			debug(e, be, method);
			return ItemStack.EMPTY;
		}
	}

	/**
	 * Method lookup that survives package-private/protected accessors:
	 * {@code getMethod} first (public, incl. inherited), then the superclass
	 * chain via {@code getDeclaredMethod} + {@code setAccessible}. Mod
	 * internals are commonly package-private — a {@code getMethod}-only
	 * lookup would silently dead the seam.
	 */
	@Nullable
	private static java.lang.reflect.Method findMethod(Class<?> type,
			String name, Class<?>... params) {
		try {
			return type.getMethod(name, params);
		} catch (NoSuchMethodException e) {
			for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
				try {
					java.lang.reflect.Method m = c.getDeclaredMethod(name, params);
					m.setAccessible(true);
					return m;
				} catch (NoSuchMethodException ignored) {
				}
			}
			return null;
		}
	}

	private static void debug(Exception e, Object be, String method) {
		LifepathMod.LOGGER.debug("Tobacconist take support: {}.{} reflective call failed: {}",
				be.getClass().getSimpleName(), method, e.toString());
	}
}
