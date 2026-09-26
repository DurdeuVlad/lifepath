package io.github.durdeuvlad.lifepath.specialization;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import io.github.durdeuvlad.lifepath.skill.SkillService;
import java.util.Map;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Specialization application + effective-value lookups (M3-2).
 *
 * <p>{@link #apply} writes the selection onto the character model:
 * <ul>
 *   <li>{@code specializationId} set;</li>
 *   <li>{@code starting_skills} levels applied only RAISED — re-selection or
 *       admin {@code /lifepath specialization set} on a progressed character
 *       never lowers an existing level;</li>
 *   <li>{@code aptitudes} override the recorded grade on the skill record;</li>
 *   <li>{@code protected_floors} set the per-skill protected floor.</li>
 * </ul>
 * XP/decay effects are READ at use time — {@code xpModifierFor} feeds the XP
 * modifier pipeline; {@code decayResistanceFor} is the M3-3 seam. Both are
 * pure lookups keyed off the model's specialization id, so a reload-time
 * definition change or removal degrades gracefully.
 */
public final class SpecializationService {
	private SpecializationService() {
	}

	public enum ApplyResult { APPLIED, UNKNOWN_SPEC }

	/** Applies the specialization onto {@code data}. Never lowers levels. */
	public static ApplyResult apply(PlayerCharacterData data, Identifier specId) {
		SpecializationDefinition def = LifepathContent.specializations().get(specId);
		if (def == null) {
			return ApplyResult.UNKNOWN_SPEC;
		}
		data.setSpecializationId(specId);

		for (Map.Entry<Identifier, Integer> e : def.startingSkills().entrySet()) {
			SkillProgress cur = SkillService.ensureProgress(data, e.getKey());
			if (cur != null && cur.level() < e.getValue()) {
				// Raise-only: starting levels never lower an existing level and
				// highestLevel must reflect the granted level immediately.
				data.setSkillProgress(e.getKey(), SkillService.clamped(e.getKey(),
						new SkillProgress(cur.xp(), e.getValue(),
								Math.max(cur.highestLevel(), e.getValue()), cur.protectedFloor(),
								cur.aptitude(), cur.lastMeaningfulUse())));
			}
		}
		for (Map.Entry<Identifier, Aptitude> e : def.aptitudes().entrySet()) {
			SkillProgress cur = SkillService.ensureProgress(data, e.getKey());
			if (cur != null) {
				data.setSkillProgress(e.getKey(), new SkillProgress(cur.xp(), cur.level(),
						cur.highestLevel(), cur.protectedFloor(), e.getValue(),
						cur.lastMeaningfulUse()));
			}
		}
		for (Map.Entry<Identifier, Integer> e : def.protectedFloors().entrySet()) {
			SkillProgress cur = SkillService.ensureProgress(data, e.getKey());
			if (cur != null && e.getValue() > cur.protectedFloor()) {
				data.setSkillProgress(e.getKey(), new SkillProgress(cur.xp(), cur.level(),
						cur.highestLevel(), e.getValue(), cur.aptitude(),
						cur.lastMeaningfulUse()));
			}
		}
		return ApplyResult.APPLIED;
	}

	/**
	 * XP multiplier contributed by the character's specialization
	 * ({@code xp_modifiers[skillId]}), or 1.0 when unset/absent/unknown.
	 */
	public static double xpModifierFor(@Nullable Identifier specId, Identifier skillId) {
		if (specId == null) {
			return 1.0;
		}
		SpecializationDefinition def = LifepathContent.specializations().get(specId);
		if (def == null) {
			return 1.0;
		}
		return def.xpModifiers().getOrDefault(skillId, 1.0);
	}

	/**
	 * Decay resistance fraction [0,1] contributed by the specialization
	 * ({@code decay_modifiers[skillId]}, e.g. 0.30 = 30% less decay).
	 * Consumed by M3-3; exposed now per the issue spec.
	 */
	public static double decayResistanceFor(@Nullable Identifier specId, Identifier skillId) {
		if (specId == null) {
			return 0.0;
		}
		SpecializationDefinition def = LifepathContent.specializations().get(specId);
		if (def == null) {
			return 0.0;
		}
		return Math.max(0.0, def.decayModifiers().getOrDefault(skillId, 0.0));
	}
}
