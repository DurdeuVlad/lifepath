package io.github.durdeuvlad.lifepath.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Skill-domain lifecycle events, Fabric-style. Server-side only — listeners
 * run on the server thread inside {@code SkillXpService}.
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
	public static final Event<LevelUp> LEVEL_UP = EventFactory.createArrayBacked(LevelUp.class,
			listeners -> (player, skillId, oldLevel, newLevel, source) -> {
				for (LevelUp listener : listeners) {
					listener.onLevelUp(player, skillId, oldLevel, newLevel, source);
				}
			});

	@FunctionalInterface
	public interface LevelUp {
		void onLevelUp(ServerPlayerEntity player, Identifier skillId,
				int oldLevel, int newLevel, ActivityEvent source);
	}
}
