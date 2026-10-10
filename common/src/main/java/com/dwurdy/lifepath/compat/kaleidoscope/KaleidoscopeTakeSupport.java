package com.dwurdy.lifepath.compat.kaleidoscope;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Reflective helpers shared by the Kaleidoscope Cookery take-out mixins. All
 * member access is reflective so a KC update that renames or reshapes internals
 * degrades to a debug log instead of a hard failure — the same convention as
 * the Overgeared compat layer.
 */
public final class KaleidoscopeTakeSupport {

	private KaleidoscopeTakeSupport() {
	}

	/**
	 * Calls a no-arg accessor on {@code be} (e.g. {@code getResult},
	 * {@code getCurrentCutStack}) and returns a defensive copy.
	 */
	public static ItemStack readStack(Object be, String method) {
		try {
			Object value = be.getClass().getMethod(method).invoke(be);
			return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
		} catch (ReflectiveOperationException | ClassCastException e) {
			debug(e, be, method);
			return ItemStack.EMPTY;
		}
	}

	/**
	 * Reads every stack exposed by a no-arg accessor that returns either a
	 * {@link List} (steamer {@code getItems}) or a NeoForge {@code IItemHandler}
	 * (shawarma {@code getOutputs}). Defensive copies throughout.
	 */
	public static List<ItemStack> readStacks(Object be, String method) {
		List<ItemStack> out = new ArrayList<>();
		try {
			Object value = be.getClass().getMethod(method).invoke(be);
			if (value instanceof List<?> list) {
				for (Object o : list) {
					out.add(o instanceof ItemStack s ? s.copy() : ItemStack.EMPTY);
				}
			} else if (value != null) {
				// IItemHandler: getSlots()/getStackInSlot(int) reflectively —
				// common has no NeoForge types on its compile classpath.
				Object slots = value.getClass().getMethod("getSlots").invoke(value);
				int count = slots instanceof Integer i ? i : 0;
				for (int i = 0; i < count; i++) {
					Object s = value.getClass()
							.getMethod("getStackInSlot", int.class).invoke(value, i);
					out.add(s instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY);
				}
			}
		} catch (ReflectiveOperationException | ClassCastException e) {
			debug(e, be, method);
		}
		return out;
	}

	/**
	 * Fires the crafting activity for {@code stack} when the taker is a real
	 * player. Non-player takers (dispensers, mobs) earn nothing.
	 */
	public static void emitTake(LivingEntity taker, @Nullable ItemStack stack) {
		if (!(taker instanceof Player player) || stack == null || stack.isEmpty()) {
			return;
		}
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}

	/**
	 * Emits the difference between two slot snapshots: for each position where
	 * the same item shrank or vanished, the missing portion is the take-out.
	 */
	public static void emitDelta(LivingEntity taker, List<ItemStack> before,
			List<ItemStack> after) {
		if (!(taker instanceof Player player)) {
			return;
		}
		int size = Math.max(before.size(), after.size());
		for (int i = 0; i < size; i++) {
			ItemStack b = i < before.size() ? before.get(i) : ItemStack.EMPTY;
			ItemStack a = i < after.size() ? after.get(i) : ItemStack.EMPTY;
			if (b.isEmpty()) {
				continue;
			}
			int removed = a.isEmpty() || !ItemStack.isSameItemSameComponents(b, a)
					? b.getCount()
					: b.getCount() - a.getCount();
			if (removed > 0) {
				ItemStack taken = b.copy();
				taken.setCount(removed);
				VanillaGameplayProducers.onCraftOutput(player, taken);
			}
		}
	}

	private static void debug(Exception e, Object be, String method) {
		LifepathMod.LOGGER.debug("KC take support: {}.{} reflective read failed: {}",
				be.getClass().getSimpleName(), method, e.toString());
	}
}
