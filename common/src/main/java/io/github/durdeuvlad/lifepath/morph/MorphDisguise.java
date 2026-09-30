package io.github.durdeuvlad.lifepath.morph;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.content.MorphFormDefinition;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
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

	/** The morphed player's disguise dims, or null when not morphed / unknown type. */
	public static @Nullable EntityDimensions dimsFor(Entity entity) {
		ResourceLocation typeId = MorphDisguised.typeIdOf(entity);
		if (typeId == null) {
			return null;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
		if (type == null) {
			if (WARNED.add(typeId)) {
				LifepathMod.LOGGER.warn("morph disguise entity type {} not in registry"
						+ " — vanilla dimensions", typeId);
			}
			return null;
		}
		return type.getDimensions();
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
