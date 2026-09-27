package io.github.durdeuvlad.lifepath.character;

import net.minecraft.resources.ResourceLocation;

/**
 * Existence view over content registries, used when sanitizing loaded character
 * data: entries referencing deleted/unknown definitions are dropped with a WARN
 * instead of corrupting the save or crashing.
 *
 * <p>The default {@link #PERMISSIVE} index accepts everything — installed until
 * M1-3's real content registries exist. Domain names are the bare strings
 * {@code "species"}, {@code "specialization"}, {@code "skill"},
 * {@code "trait"}, {@code "condition"}, {@code "attunement"}, {@code "ability"},
 * {@code "resource"}.
 */
@FunctionalInterface
public interface ContentIndex {
	ContentIndex PERMISSIVE = (domain, id) -> true;

	boolean exists(String domain, ResourceLocation id);
}
