package io.github.durdeuvlad.lifepath.morph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * M-5 morph gate coverage for {@link MorphDisguise#isResultSlot}: every
 * station output slot a morphed player must be unable to take from. The
 * pickable denial itself ({@code mayPickup} / {@code clicked}) lives in
 * mixins — verified in-game like every Minecraft-runtime seam — but the
 * slot classification is plain data and pinned here.
 */
class MorphResultSlotGateTest {
	@BeforeAll
	static void bootMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static Inventory inventory() {
		return new Inventory(null);
	}

	private static int resultSlotCount(AbstractContainerMenu menu) {
		return (int) menu.slots.stream().filter(MorphDisguise::isResultSlot).count();
	}

	@Test
	void craftingResultIsAResultSlot() {
		CraftingMenu menu = new CraftingMenu(0, inventory());
		assertTrue(MorphDisguise.isResultSlot(menu.getSlot(CraftingMenu.RESULT_SLOT)),
				"crafting table output must be gated while morphed");
		assertTrue(MorphDisguise.isResultSlot(menu,
				menu.getSlot(CraftingMenu.RESULT_SLOT)),
				"menu-aware check must agree");
		assertEquals(1, resultSlotCount(menu), "only the result slot is gated");
	}

	@Test
	void anvilResultIsAResultSlot() {
		// The anvil's output is an anonymous Slot subclass on a
		// ResultContainer — classification must key on the container, not
		// the slot class (which can't even be named by a mixin). Its
		// mayPickup override also bypasses the Slot-level inject, so the
		// clicked seam is what actually gates it — exercise both views.
		AnvilMenu menu = new AnvilMenu(0, inventory());
		assertTrue(MorphDisguise.isResultSlot(menu.getSlot(AnvilMenu.RESULT_SLOT)));
		assertTrue(MorphDisguise.isResultSlot(menu,
				menu.getSlot(AnvilMenu.RESULT_SLOT)));
		assertEquals(1, resultSlotCount(menu));
	}

	@Test
	void menuAwareCheckAgreesEverywhere() {
		// The menu-aware overload adds exactly one branch — the loom —
		// and must otherwise mirror the slot-classification verdicts.
		// (The loom branch itself can't be exercised headlessly: the
		// LoomMenu constructor dereferences player.registryAccess(), and
		// a Player needs a live Level.)
		CraftingMenu menu = new CraftingMenu(0, inventory());
		for (int i = 0; i < menu.slots.size(); i++) {
			assertEquals(MorphDisguise.isResultSlot(menu.getSlot(i)),
					MorphDisguise.isResultSlot(menu, menu.getSlot(i)),
					"menu-aware check disagrees at slot " + i);
		}
	}

	@Test
	void resultContainerSlotsAreGated() {
		// Smithing/stonecutter/loom/grindstone/cartography results are plain
		// Slots on a ResultContainer — the container is the signal, not the
		// (often anonymous) slot class.
		assertTrue(MorphDisguise.isResultSlot(
				new Slot(new ResultContainer(), 0, 0, 0)));
	}

	@Test
	void furnaceAndMerchantResultsAreResultSlots() {
		// Dedicated slot classes on non-ResultContainer backing containers.
		assertTrue(MorphDisguise.isResultSlot(
				new FurnaceResultSlot(null, new SimpleContainer(1), 0, 0, 0)));
		assertTrue(MorphDisguise.isResultSlot(
				new MerchantResultSlot(null, null, null, 0, 0, 0)));
	}

	@Test
	void pickAllWouldTakeResultFollowsTheGatherPredicate() {
		// The predicate replays vanilla's per-slot gather test, so a
		// covered result slot must NOT trip the up-front refusal: the
		// crafting result is skipped by the gather itself —
		// CraftingMenu.canTakeItemForPickAll excludes the result container
		// outright, and the Slot-level mayPickup gate denies it besides.
		// Pinning this matters: a broader predicate would stop a morphed
		// player from double-click gathering their own inventory whenever
		// a craftable result coincidentally matched the carried stack.
		CraftingMenu menu = new CraftingMenu(0, inventory());
		// Vanilla bails on an empty carried stack before gathering at all.
		assertFalse(MorphDisguise.pickAllWouldTakeResult(menu, null));

		menu.getSlot(CraftingMenu.RESULT_SLOT).set(new ItemStack(Items.IRON_INGOT));
		// A non-matching carried stack can't draw from the result slot.
		menu.setCarried(new ItemStack(Items.GOLD_INGOT));
		assertFalse(MorphDisguise.pickAllWouldTakeResult(menu, null));
		// A matching stack in the result doesn't reach the refusal either
		// — the gather would skip this slot anyway.
		menu.setCarried(new ItemStack(Items.IRON_INGOT));
		assertFalse(MorphDisguise.pickAllWouldTakeResult(menu, null),
				"a covered result slot must not block a safe gather click");
	}

	@Test
	void pickAllWouldTakeResultFiresForGatherableResults() {
		// The positive branch: a result slot vanilla's gather would
		// actually take — takeable per mayPickup, not excluded by the
		// menu's canTakeItemForPickAll. That is the anvil/loom shape
		// (anvil's mayPickup override bypasses the Slot inject; the loom
		// result is unclassifiable without menu context), which can't be
		// constructed headlessly, so a bare menu + a ResultContainer slot
		// stands in. (No mixin under JUnit — mayPickup reports vanilla's
		// constant true; in production the inject would deny this slot.)
		BareMenu menu = new BareMenu(new ResultContainer());
		menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT));
		menu.setCarried(new ItemStack(Items.IRON_INGOT));
		assertTrue(MorphDisguise.pickAllWouldTakeResult(menu, null),
				"a gatherable result must be refused up front");
	}

	@Test
	void pickAllWouldTakeResultIsComponentAware() {
		// Station outputs almost always differ in components from any
		// carried copy (anvil rename/repair, smithing upgrade) — the guard
		// must only fire on a true same-item-same-components match, not a
		// bare item match, or ordinary gathers would false-positive.
		BareMenu menu = new BareMenu(new ResultContainer());
		ItemStack result = new ItemStack(Items.IRON_INGOT);
		result.set(DataComponents.CUSTOM_NAME, Component.literal("repaired"));
		menu.getSlot(0).set(result);
		menu.setCarried(new ItemStack(Items.IRON_INGOT));
		assertFalse(MorphDisguise.pickAllWouldTakeResult(menu, null),
				"different components must not count as gatherable");
	}

	@Test
	void inventoryAndGridSlotsAreNotResultSlots() {
		CraftingMenu menu = new CraftingMenu(0, inventory());
		for (int i = 1; i < menu.slots.size(); i++) {
			assertFalse(MorphDisguise.isResultSlot(menu.getSlot(i)),
					"non-result slot " + i + " must stay usable");
		}
		assertFalse(MorphDisguise.isResultSlot(
				new Slot(new SimpleContainer(1), 0, 0, 0)),
				"an ordinary container slot must stay usable");
	}

	/**
	 * Minimal menu for exercising {@code pickAllWouldTakeResult} against a
	 * result slot vanilla's gather would take: no
	 * {@code canTakeItemForPickAll} override (base returns true) and the
	 * slot's {@code mayPickup} is the constant-true base.
	 */
	private static final class BareMenu extends AbstractContainerMenu {
		private BareMenu(Container container) {
			super(null, 0);
			addSlot(new Slot(container, 0, 0, 0));
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
