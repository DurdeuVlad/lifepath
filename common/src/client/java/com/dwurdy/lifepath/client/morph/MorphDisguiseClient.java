package com.dwurdy.lifepath.client.morph;

import com.mojang.blaze3d.vertex.PoseStack;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.client.mixin.WalkAnimationStateAccessor;
import com.dwurdy.lifepath.morph.MorphDisguised;
import com.dwurdy.lifepath.platform.ClientOnly;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * M-4 render disguise (docs/MORPH_FEATURE.md — "disguise, not possess").
 * The morphed player's render call is redirected at
 * {@code EntityRenderDispatcher.render} to a cached, never-spawned entity
 * of the form's {@code entity_type}, posed and rotated to match the
 * player each frame. Covers the local player (F5) and remote players
 * alike — both arrive via the synced entity-data flag.
 *
 * <p>The cached entity is a render prop: it never ticks, never joins the
 * level's entity list, and carries no name — so the vanilla nametag path
 * inside the dispatcher never fires (the locked "hidden while morphed"
 * policy). Entities are recreated per level switch and dropped on
 * disconnect via {@link #clear()}.
 */
@ClientOnly
public final class MorphDisguiseClient {
	private MorphDisguiseClient() {
	}

	/** typeId → render prop. Recreated on level change — entities pin a Level ref. */
	private static final Map<ResourceLocation, Entity> CACHE = new HashMap<>();
	private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
	private static @Nullable Level cacheLevel;

	/** Drop all render props on disconnect — a stale Level ref must never outlive the world. */
	public static void clear() {
		CACHE.clear();
		cacheLevel = null;
	}

	/**
	 * Renders the morphed player's disguise instead of the player. Returns
	 * true when the swap happened (caller cancels the vanilla render);
	 * false — unresolvable type, failed create — falls back to the normal
	 * player render so a bad datapack can never blank a player.
	 */
	public static boolean render(Player player, EntityRenderDispatcher dispatcher,
			double x, double y, double z, float yaw, float tickDelta,
			PoseStack matrices, MultiBufferSource buffers, int light) {
		ResourceLocation typeId = MorphDisguised.typeIdOf(player);
		if (typeId == null) {
			return false;
		}
		Entity disguise = disguiseFor(player.level(), typeId);
		if (disguise == null) {
			return false;
		}
		copyState(disguise, player);
		dispatcher.render(disguise, x, y, z, yaw, tickDelta, matrices, buffers, light);
		return true;
	}

	private static @Nullable Entity disguiseFor(Level level, ResourceLocation typeId) {
		if (cacheLevel != level) {
			CACHE.clear();
			cacheLevel = level;
		}
		Entity cached = CACHE.get(typeId);
		if (cached != null) {
			return cached;
		}
		// getHolder, not get: ENTITY_TYPE is a DefaultedRegistry — get()
		// answers AIR for unknown ids rather than null.
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getHolder(typeId)
				.map(net.minecraft.core.Holder::value).orElse(null);
		Entity created = type == null ? null : type.create(level);
		if (created == null) {
			if (WARNED.add(typeId)) {
				LifepathMod.LOGGER.warn("morph disguise: cannot create {} "
						+ "— falling back to player render", typeId);
			}
			return null;
		}
		// Forms are adults — a baby animal's dims would contradict the
		// server-side hitbox (always the type's adult dimensions).
		if (created instanceof AgeableMob ageable) {
			ageable.setBaby(false);
		}
		CACHE.put(typeId, created);
		return created;
	}

	/**
	 * The minimal living-state mirror — rotation, pose, swing, hurt/limb
	 * phase — so the animal reads as "the player, shaped differently"
	 * instead of a frozen prop. Only render-visible fields are copied;
	 * position never is (the dispatcher call places it at the player's
	 * interpolated spot).
	 */
	private static void copyState(Entity disguise, Player player) {
		disguise.tickCount = player.tickCount;
		disguise.setYRot(player.getYRot());
		disguise.setXRot(player.getXRot());
		disguise.yRotO = player.yRotO;
		disguise.xRotO = player.xRotO;
		disguise.setPose(player.getPose());
		disguise.setOnGround(player.onGround());
		if (disguise instanceof LivingEntity living) {
			living.yHeadRot = player.yHeadRot;
			living.yHeadRotO = player.yHeadRotO;
			living.yBodyRot = player.yBodyRot;
			living.yBodyRotO = player.yBodyRotO;
			living.hurtTime = player.hurtTime;
			living.deathTime = player.deathTime;
			living.attackAnim = player.attackAnim;
			living.oAttackAnim = player.oAttackAnim;
			living.swinging = player.swinging;
			living.swingTime = player.swingTime;
			living.swingingArm = player.swingingArm;
			var access = (WalkAnimationStateAccessor) living.walkAnimation;
			var source = (WalkAnimationStateAccessor) player.walkAnimation;
			access.lifepath$setPosition(source.lifepath$position());
			access.lifepath$setSpeed(source.lifepath$speed());
			access.lifepath$setSpeedOld(source.lifepath$speedOld());
		}
	}
}
