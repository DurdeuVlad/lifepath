package com.dwurdy.lifepath.compat.builder;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.skill.OutcomeService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Shared emit helper for Builder-profession workstations that bypass the
 * vanilla {@code ResultSlot} take path (Chipped's {@code WorkbenchMenu.craft}
 * mutates player-inventory slots in place and fires no craft event). Free of
 * foreign-mod types — the mixins hand over the menu + chosen output, so this
 * class loads whether or not the station mod is present.
 *
 * <p>Emits the {@code lifepath:crafting} activity with a station marker tag
 * ({@code lifepath:masonry}) so xp_source data owns all weighting, then runs
 * construction outcome rules on the produced stack — publish first, outcome
 * fault-isolated, same ordering as the vanilla craft path.
 */
public final class BuilderStationTakeSupport {

	private BuilderStationTakeSupport() {
	}

	/**
	 * Attended station craft: {@code menu} is the open station menu (used
	 * only to find the owning player through its player-inventory slots —
	 * fully public API, no foreign-member access), {@code output} is the
	 * produced stack, {@code markerPath} is the {@code lifepath:*} station
	 * marker the xp_source required_tags match on.
	 *
	 * <p>The hook fires before the menu's own validation (HEAD inject), so
	 * this support re-checks the station's published result list for a
	 * matching stack before publishing — mirroring the menu's own
	 * early-return gate so replayed/invalid craft calls pay nothing. When
	 * the foreign menu exposes no result list the check degrades to
	 * pass-through (the menu still validates internally; worst case is a
	 * publish on a no-op craft, same as the vanilla take path).
	 */
	public static void emitStationCraft(AbstractContainerMenu menu,
			@Nullable ItemStack output, String markerPath) {
		if (output == null || output.isEmpty()
				|| !matchesStationResults(menu, output)) {
			return;
		}
		Player owner = playerOf(menu);
		if (!(owner instanceof ServerPlayer player) || isAutomation(player)) {
			return;
		}
		Set<ResourceLocation> itemTags = output.getTags()
				.map(TagKey::location)
				.collect(Collectors.toCollection(HashSet::new));
		ResourceLocation marker = LifepathMod.id(markerPath);
		itemTags.add(marker);
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(output.getItem());
		ActivityDispatcher.publish(ActivityEvents.crafting(player, itemId,
				itemTags, Map.of("count", Integer.toString(output.getCount()),
						"station", markerPath),
				ActivityEvent.Cause.PLAYER));
		try {
			OutcomeService.apply(player, ActivityTypes.CRAFTING, itemId,
					itemTags, output);
		} catch (Throwable t) {
			LifepathMod.LOGGER.error("Builder station outcome apply failed "
					+ "(activity already published): {}", t.toString());
		}
	}

	/**
	 * Mirrors the station menu's own output validation: the foreign
	 * {@code results()} accessor (Chipped publishes the valid craft
	 * outputs there) must contain a stack matching {@code output} by
	 * item+components. Reflective so a menu without that accessor
	 * degrades to pass-through rather than failing the emit.
	 */
	private static boolean matchesStationResults(AbstractContainerMenu menu,
			ItemStack output) {
		try {
			Object results = menu.getClass().getMethod("results").invoke(menu);
			if (!(results instanceof java.util.List<?> list)) {
				return true;
			}
			for (Object o : list) {
				if (o instanceof ItemStack candidate
						&& ItemStack.isSameItemSameComponents(candidate, output)) {
					return true;
				}
			}
			return false;
		} catch (ReflectiveOperationException | ClassCastException e) {
			return true;
		}
	}

	/**
	 * Finds the menu's owning player via the first slot backed by a real
	 * {@link Inventory}. Station menus always add player-inventory slots;
	 * the inventory's {@code player} field is the live owner (a
	 * {@link ServerPlayer} on the server side).
	 */
	@Nullable
	private static Player playerOf(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot != null && slot.container instanceof Inventory inv) {
				return inv.player;
			}
		}
		return null;
	}

	private static boolean isAutomation(ServerPlayer player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
