package com.dwurdy.lifepath.compat.mapatlases;

import com.dwurdy.lifepath.producer.VanillaGameplayProducers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Emit helper for the Map Atlases cartography-table seam. The atlas is
 * updated inside the <em>vanilla</em> {@code CartographyTableMenu} result slot
 * (map_atlases only reshapes what the result shows); taking it is the
 * production moment — updated {@code map_atlases:atlas} or extracted
 * {@code minecraft:filled_map} stacks. Vanilla cartography outputs (cloned,
 * scaled, locked maps) ride the same take-out and get the same treatment.
 */
public final class MapAtlasesCartography {
	private MapAtlasesCartography() {
	}

	/**
	 * Cartography result-slot take — delegates to the vanilla crafting-output
	 * producer so the event carries the taken stack's real item tags (the
	 * scholarly tag routes) and outcome scaling applies.
	 */
	public static void emitTake(Player player, ItemStack stack) {
		VanillaGameplayProducers.onCraftOutput(player, stack);
	}
}
