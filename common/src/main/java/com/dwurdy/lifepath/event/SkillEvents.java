package com.dwurdy.lifepath.event;

import com.dwurdy.lifepath.platform.SimpleEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Skill-domain lifecycle events. Server-side only — listeners run on the
 * server thread inside {@code SkillXpService}.
 *
 * <p>M13-3: backed by {@link SimpleEvent} — Lifepath's own events need no
 * loader machinery.
 */
public final class SkillEvents {
	private SkillEvents() {
	}

	/**
	 * Fired AFTER a skill's level increased (never fires on a no-change or
	 * decrease). Listeners run on the server thread, inside the award call —
	 * a listener that re-enters {@code SkillXpService.awardXp} for the same
	 * skill will recurse; keep listeners non-mutating or guard them.
	 */
	public static final SimpleEvent<LevelUp> LEVEL_UP = new SimpleEvent<>(
			listeners -> (player, skillId, oldLevel, newLevel, source) -> {
				for (LevelUp listener : listeners) {
					listener.onLevelUp(player, skillId, oldLevel, newLevel, source);
				}
			});

	@FunctionalInterface
	public interface LevelUp {
		void onLevelUp(ServerPlayer player, ResourceLocation skillId,
				int oldLevel, int newLevel, ActivityEvent source);
	}
}
