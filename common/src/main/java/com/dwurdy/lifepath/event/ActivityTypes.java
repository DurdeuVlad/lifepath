package com.dwurdy.lifepath.event;

import net.minecraft.resources.ResourceLocation;

/**
 * Canonical activity-type ids. These are domain VOCABULARY (what kinds of
 * activity exist at all), not content identity — adding a type is a constant
 * or a datapack reference, never a new class.
 */
public final class ActivityTypes {
	private ActivityTypes() {
	}

	public static final ResourceLocation MINING = ResourceLocation.fromNamespaceAndPath("lifepath", "mining");
	public static final ResourceLocation FARMING = ResourceLocation.fromNamespaceAndPath("lifepath", "farming");
	public static final ResourceLocation SMITHING = ResourceLocation.fromNamespaceAndPath("lifepath", "smithing");
	public static final ResourceLocation FISHING = ResourceLocation.fromNamespaceAndPath("lifepath", "fishing");
	public static final ResourceLocation CRAFTING = ResourceLocation.fromNamespaceAndPath("lifepath", "crafting");
	public static final ResourceLocation COMBAT = ResourceLocation.fromNamespaceAndPath("lifepath", "combat");
	/** Ranged kills — emitted alongside {@link #COMBAT} for projectile kills (M8-3). */
	public static final ResourceLocation ARCHERY = ResourceLocation.fromNamespaceAndPath("lifepath", "archery");
	/** Hostile damage survived — emitted when a mob damages a player (M8-3). */
	public static final ResourceLocation DEFENCE = ResourceLocation.fromNamespaceAndPath("lifepath", "defence");
	/** Potion take-outs from a brewing stand (M24 Brewer). */
	public static final ResourceLocation BREWING = ResourceLocation.fromNamespaceAndPath("lifepath", "brewing");
	/** Animal care: feed/breed/birth/milk/shear/groom interactions. */
	public static final ResourceLocation HUSBANDRY = ResourceLocation.fromNamespaceAndPath("lifepath", "husbandry");
	/** Song/spell performance — SpellEngine SPELL_CAST completions. */
	public static final ResourceLocation PERFORMANCE = ResourceLocation.fromNamespaceAndPath("lifepath", "performance");
	/** Instrument play — melody starts (Immersive Melodies when present). */
	public static final ResourceLocation MUSIC = ResourceLocation.fromNamespaceAndPath("lifepath", "music");
	/** Medical diagnosis — reserved; emitters arrive with Medic mod intake. */
	public static final ResourceLocation DIAGNOSIS = ResourceLocation.fromNamespaceAndPath("lifepath", "diagnosis");
	/** Medical treatment — reserved; emitters arrive with Medic mod intake. */
	public static final ResourceLocation TREATMENT = ResourceLocation.fromNamespaceAndPath("lifepath", "treatment");
}
