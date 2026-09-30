package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.SkillEvents;
import io.github.durdeuvlad.lifepath.feedback.FeedbackService;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.unlock.UnlockService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Milestone payoff (B5): turns {@code skill.milestones[].effects} into real
 * grants. Listens on {@link SkillEvents#LEVEL_UP}; every milestone crossed in
 * {@code (oldLevel, newLevel]} contributes its effect refs — ability ids —
 * into {@code PlayerCharacterData.unlocks[]} via {@link UnlockService#grant},
 * which is idempotent (decay-and-relearn never double-grants, a re-crossed
 * milestone stays silent) and marks the character dirty + re-syncs, so the
 * new ability reaches the Character screen/HUD on the next sync.
 *
 * <p>Fail-closed by design: an effect ref that doesn't resolve to an ability
 * definition warns once per crossing and is skipped — a malformed datapack
 * can't crash a level-up. Refs naming non-ability content (a future effect
 * domain) are datapack-authoring errors surfaced in logs, not silent magic.
 *
 * <p>Server-authoritative: runs only on the LEVEL_UP server event — clients
 * have no path in.
 */
public final class SkillMilestoneService {
	private SkillMilestoneService() {
	}

	private static boolean initialized;

	/** Registers the LEVEL_UP listener. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		SkillEvents.LEVEL_UP.register(SkillMilestoneService::onLevelUp);
	}

	private static void onLevelUp(ServerPlayer player, ResourceLocation skillId,
			int oldLevel, int newLevel, ActivityEvent source) {
		SkillDefinition def = LifepathContent.skills().get(skillId);
		if (def == null) {
			return;
		}
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		if (data == null) {
			return;
		}
		for (ResourceLocation ref : grantsFor(def, oldLevel, newLevel)) {
			if (LifepathContent.abilities().get(ref) == null) {
				LifepathMod.LOGGER.warn(
						"skill {} milestone grants unknown ability '{}' — skipped",
						skillId, ref);
				continue;
			}
			if (UnlockService.grant(data, player, ref)) {
				FeedbackService.abilityLearned(player, ref);
			}
		}
	}

	/**
	 * Effect refs for milestones crossed in {@code (oldLevel, newLevel]} —
	 * visible for tests; dedupes refs shared by adjacent milestones.
	 */
	static List<ResourceLocation> grantsFor(SkillDefinition def, int oldLevel, int newLevel) {
		List<ResourceLocation> out = new ArrayList<>();
		for (SkillDefinition.Milestone m : def.milestones()) {
			if (m.level() > oldLevel && m.level() <= newLevel) {
				for (ResourceLocation ref : m.effectRefs()) {
					if (!out.contains(ref)) {
						out.add(ref);
					}
				}
			}
		}
		return out;
	}
}
