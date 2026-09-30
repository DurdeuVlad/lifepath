package io.github.durdeuvlad.lifepath.morph;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The morph disguise flag carried on the player entity's synced data
 * (morph feature M-4). The server stamps the form's {@code entity_type}
 * id on every state transition; entity data replicates it to every
 * tracking client automatically — no bespoke payload, no late-tracker
 * gap. Empty string = not morphed.
 *
 * <p>Implemented by {@code PlayerMorphMixin}; anything checking disguise
 * state goes through {@link #typeIdOf(Entity)} so non-player entities and
 * missing-data edge cases collapse to "not morphed".
 */
public interface MorphDisguised {
	/** The synced morph entity-type id, or "" when not morphed. */
	String lifepath$morphEntityType();

	/** Server-side write — clients never set this. */
	void lifepath$setMorphEntityType(String entityTypeId);

	/** True when {@code key} is the morph data accessor (synced-data watch). */
	boolean lifepath$isMorphDataKey(EntityDataAccessor<?> key);

	/**
	 * The morphed entity's disguise type, or null when the entity isn't a
	 * morphed player (or the synced value is malformed — written only by
	 * the server, but never trust wire bytes blindly).
	 */
	static @Nullable net.minecraft.resources.ResourceLocation typeIdOf(Entity entity) {
		if (!(entity instanceof MorphDisguised d)) {
			return null;
		}
		String id = d.lifepath$morphEntityType();
		return id.isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
	}
}
