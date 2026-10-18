package com.dwurdy.lifepath.compat.overgeared;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import java.lang.reflect.Field;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Emit + gating helpers for the Overgeared station menus whose result slots
 * are plain {@code Slot}/{@code SlotItemHandler} subclasses
 * ({@code RockKnappingMenu$2}, {@code FletchingStationMenu$6},
 * {@code AlloySmelterMenu$1}, {@code NetherAlloySmelterMenu$2}) — they never
 * touch vanilla {@code ResultSlot}/{@code FurnaceResultSlot}, so neither the
 * crafting-take mixin nor the furnace mixin sees their outputs. Verified
 * against overgeared-neoforge 1.6.17 bytecode.
 *
 * <p>Free of overgeared and neoforge types: the mixins hand over vanilla
 * {@code onTake} parameters, and menu internals ({@code player}, the result
 * container) are reached reflectively, so this class loads without the mod
 * and an Overgeared refactor degrades to a skipped check.
 */
public final class OvergearedStationSupport {
	private OvergearedStationSupport() {
	}

	/**
	 * Forge-station take-out: the taken stack is emitted as a smithing
	 * activity and mutated in place by outcome scaling — the same lane the
	 * anvil and vanilla smithing-table takes use. Knapping has no station
	 * block (the menu opens from {@code knappable_rock}), so callers pass a
	 * pseudo workstation id under the {@code overgeared} namespace.
	 */
	public static void emitTake(Player player, ItemStack stack,
			String workstationPath) {
		VanillaGameplayProducers.onForgeTake(player, stack,
				ResourceLocation.fromNamespaceAndPath(
						OvergearedCompat.MOD_ID, workstationPath));
	}

	/**
	 * Material-gate enforcement for station menus that compute their result
	 * in a private {@code updateResult}/{@code updateResultSlot} — injected
	 * at TAIL, mirroring {@code SmithingScreenHandlerMixin}'s
	 * {@code createResult} gate. The player lives in a private {@code player}
	 * field on both menus; the result container is {@code result}
	 * (fletching, a {@code ResultContainer}) or {@code resultContainer}
	 * (knapping, a {@code SimpleContainer}) — both cleared by {@code setItem}
	 * whose {@code setChanged} can't recurse back into the result
	 * computation. Denied results vanish BEFORE take-out: fail-closed, same
	 * as the smithing table. No blueprint slot exists on these stations.
	 */
	public static void gateMenuResult(AbstractContainerMenu menu,
			String resultField) {
		Object player = readField(menu, "player");
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		Object container = readField(menu, resultField);
		if (!(container instanceof Container result) || result.isEmpty()) {
			return;
		}
		for (int i = 0; i < result.getContainerSize(); i++) {
			ItemStack produced = result.getItem(i);
			var denial = com.dwurdy.lifepath.skill.MaterialGates
					.denialFor(serverPlayer, produced, false);
			if (denial != null) {
				result.setItem(i, ItemStack.EMPTY);
				com.dwurdy.lifepath.skill.MaterialGates
						.notifyDenied(serverPlayer, denial);
				return;
			}
		}
	}

	@Nullable
	private static Object readField(Object owner, String name) {
		if (owner == null) {
			return null;
		}
		try {
			Field f = owner.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return f.get(owner);
		} catch (Throwable t) {
			LifepathMod.LOGGER.debug(
					"overgeared station support: {}.{} reflective read failed: {}",
					owner.getClass().getSimpleName(), name, t.toString());
			return null;
		}
	}
}
