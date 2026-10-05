package com.dwurdy.lifepath.morph;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.content.MorphFormDefinition;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Shared disguise plumbing (morph feature M-4): resolves the synced
 * entity-type id on a player into the {@link EntityDimensions} the
 * hitbox/eye-height seam needs, and stamps the synced flag on every
 * lifecycle transition. Runs on both sides — the server uses it for the
 * real collision box, clients for render + local simulation.
 *
 * <p>{@code EntityDimensions} already carries the animal's eye height
 * (the record's {@code eyeHeight} component), so returning the form's
 * dims fixes hitbox AND camera height in one seam —
 * {@code Entity.refreshDimensions} copies {@code dims.eyeHeight()} into
 * the entity's {@code eyeHeight} field.
 */
public final class MorphDisguise {
	private MorphDisguise() {
	}

	/** Entity types that failed registry lookup — warn once, not per tick. */
	private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();

	/** True while the entity is a morphed player — the both-sides check the
	 *  M-5 restriction seams use (synced data, so client prediction agrees
	 *  with the server). */
	public static boolean isDisguised(Entity entity) {
		return MorphDisguised.typeIdOf(entity) != null;
	}

	/**
	 * A slot a morphed player must not take items from (morph feature M-5):
	 * every station's output slot. {@code ResultContainer} is the shared
	 * backing container for crafting, anvil, smithing, stonecutter,
	 * grindstone, and cartography results — including the anonymous Slot
	 * subclasses those menus create — while furnace and merchant results
	 * keep their own slot types on other containers.
	 *
	 * <p>The loom is the lone exception: its output slot is an anonymous
	 * {@code Slot} on a plain {@code SimpleContainer}, so no slot- or
	 * container-class signal exists. It is identified through the owning
	 * menu in {@link #isResultSlot(AbstractContainerMenu, Slot)} — the
	 * {@code clicked} funnel's variant, which also covers the
	 * {@code PICKUP_ALL} gather via {@link #pickAllWouldTakeResult}.
	 * {@code Slot}-level callers ({@code mayPickup}, {@code tryRemove})
	 * have no menu context, so the loom slot answers "not a result" to
	 * them — unreachable either way, since every extraction passes
	 * through {@code clicked}.
	 */
	public static boolean isResultSlot(Slot slot) {
		return slot instanceof ResultSlot
				|| slot instanceof FurnaceResultSlot
				|| slot instanceof MerchantResultSlot
				|| slot.container instanceof ResultContainer;
	}

	/**
	 * Menu-aware result-slot check for the {@code clicked} funnel —
	 * everything {@link #isResultSlot(Slot)} covers, plus the loom result
	 * identified via {@link LoomMenu#getResultSlot()}.
	 */
	public static boolean isResultSlot(AbstractContainerMenu menu, Slot slot) {
		if (isResultSlot(slot)) {
			return true;
		}
		return menu instanceof LoomMenu loom && loom.getResultSlot() == slot;
	}

	/**
	 * Whether a double-click gather ({@code ClickType.PICKUP_ALL}) on this
	 * menu would draw from a result slot. The gather walks EVERY slot —
	 * the clicked slot being innocent proves nothing — so the predicate
	 * replicates vanilla's per-slot test from {@code doClick} verbatim:
	 * {@code hasItem}, {@code canItemQuickReplace}, {@code mayPickup},
	 * {@code canTakeItemForPickAll}. Mirroring it matters: covered result
	 * slots report {@code mayPickup} → false through the Slot-level inject,
	 * and crafting/smithing/stonecutter/cartography results are excluded
	 * by vanilla's own {@code canTakeItemForPickAll} override — the gather
	 * skips all of them by itself, so they must NOT trip this refusal
	 * (otherwise a matching stack sitting in a 2×2 result would block
	 * double-click gathering anywhere in the inventory).
	 *
	 * <p>What survives the filter is exactly the up-front refusal's real
	 * job: result slots whose take the per-slot gate can't see — the
	 * anvil's {@code ItemCombinerMenu$2}, whose {@code mayPickup} override
	 * bypasses the Slot inject, and the loom result, which the
	 * slot-level classification can't identify (no menu context).
	 */
	public static boolean pickAllWouldTakeResult(AbstractContainerMenu menu,
			Player player) {
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty()) {
			return false;
		}
		for (Slot slot : menu.slots) {
			// hasItem first, as in the vanilla loop: canItemQuickReplace
			// answers true for EMPTY slots (its quick-move clause).
			if (slot.hasItem() && isResultSlot(menu, slot)
					&& AbstractContainerMenu.canItemQuickReplace(slot, carried, true)
					&& slot.mayPickup(player)
					&& menu.canTakeItemForPickAll(carried, slot)) {
				return true;
			}
		}
		return false;
	}

	/** The morphed player's disguise dims, or null when not morphed / unknown type. */
	public static @Nullable EntityDimensions dimsFor(Entity entity) {
		ResourceLocation typeId = MorphDisguised.typeIdOf(entity);
		if (typeId == null) {
			return null;
		}
		// getHolder, not get: ENTITY_TYPE is a DefaultedRegistry — get()
		// answers AIR for unknown ids rather than null.
		var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(typeId).orElse(null);
		if (holder == null) {
			if (WARNED.add(typeId)) {
				LifepathMod.LOGGER.warn("morph disguise entity type {} not in registry"
						+ " — vanilla dimensions", typeId);
			}
			return null;
		}
		return holder.value().getDimensions();
	}

	/**
	 * Writes the synced disguise flag. Stamped before
	 * {@code refreshDimensions()} at every transition site so the dims
	 * seam sees the new state when the box is recomputed.
	 */
	public static void stamp(ServerPlayer player, MorphFormDefinition form) {
		if (player instanceof MorphDisguised d) {
			d.lifepath$setMorphEntityType(form.entityType().toString());
		}
	}

	/** Clears the synced disguise flag (demorph, re-pick, admin clear). */
	public static void clear(ServerPlayer player) {
		if (player instanceof MorphDisguised d) {
			d.lifepath$setMorphEntityType("");
		}
	}
}
