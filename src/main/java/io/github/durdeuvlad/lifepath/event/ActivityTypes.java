package io.github.durdeuvlad.lifepath.event;

import net.minecraft.util.Identifier;

/**
 * Canonical activity-type ids. These are domain VOCABULARY (what kinds of
 * activity exist at all), not content identity — adding a type is a constant
 * or a datapack reference, never a new class.
 */
public final class ActivityTypes {
	private ActivityTypes() {
	}

	public static final Identifier MINING = Identifier.of("lifepath", "mining");
	public static final Identifier FARMING = Identifier.of("lifepath", "farming");
	public static final Identifier SMITHING = Identifier.of("lifepath", "smithing");
	public static final Identifier FISHING = Identifier.of("lifepath", "fishing");
	public static final Identifier CRAFTING = Identifier.of("lifepath", "crafting");
	public static final Identifier COMBAT = Identifier.of("lifepath", "combat");
	/** Ranged kills — emitted alongside {@link #COMBAT} for projectile kills (M8-3). */
	public static final Identifier ARCHERY = Identifier.of("lifepath", "archery");
	/** Hostile damage survived — emitted when a mob damages a player (M8-3). */
	public static final Identifier DEFENCE = Identifier.of("lifepath", "defence");
}
