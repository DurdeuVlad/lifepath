package com.dwurdy.lifepath.client.ability;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side entity highlights (M4-3). The server sends entity ids + a
 * duration ONLY to players meant to see them; the store renders a soft
 * particle halo around each entity for the duration — a genuinely private
 * highlight (nothing is written to entity state, so no other client sees it).
 */
public final class ClientHighlights {
	private ClientHighlights() {}

	private static final Map<Integer, Integer> ACTIVE = new ConcurrentHashMap<>();
	private static int tick;

	/** Records highlighted entity network-ids with a duration in client ticks. */
	public static void add(java.util.List<Integer> entityIds, int durationTicks) {
		for (int id : entityIds) {
			ACTIVE.merge(id, durationTicks, Math::max);
		}
	}

	/** Ticked once per client tick from {@code LifepathClient}. */
	public static void tick(Minecraft client) {
		if (client.level == null || ACTIVE.isEmpty()) {
			return;
		}
		tick++;
		Iterator<Map.Entry<Integer, Integer>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Integer> e = it.next();
			if (e.setValue(e.getValue() - 1) <= 0) {
				it.remove();
				continue;
			}
			if (tick % 4 != 0) {
				continue; // particles every 4 ticks — halo, not spam
			}
			var entity = client.level.getEntity(e.getKey());
			if (entity == null) {
				continue; // dead or left view — entry still counts down
			}
			Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
			for (int i = 0; i < 4; i++) {
				double a = (tick * 0.4) + i * (Math.PI / 2);
				client.level.addParticle(ParticleTypes.END_ROD,
						center.x + Math.cos(a) * 0.6, center.y, center.z + Math.sin(a) * 0.6,
						0, 0.03, 0);
			}
		}
	}

	/** Clears all highlights — called on disconnect. */
	public static void clear() {
		ACTIVE.clear();
	}
}
