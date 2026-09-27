package io.github.durdeuvlad.lifepath.specialization;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.skill.LevelCurves;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import io.github.durdeuvlad.lifepath.skill.SkillService;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
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
	public static ApplyResult apply(PlayerCharacterData data, ResourceLocation specId) {
		SpecializationDefinition def = LifepathContent.specializations().get(specId);
		if (def == null) {
			return ApplyResult.UNKNOWN_SPEC;
		}
		data.setSpecializationId(specId);

		long now = System.currentTimeMillis();
		for (Map.Entry<ResourceLocation, Integer> e : def.startingSkills().entrySet()) {
			SkillProgress cur = SkillService.ensureProgress(data, e.getKey());
			if (cur == null) {
				continue;
			}
			int target = Math.min(e.getValue(), SkillService.maxLevel(e.getKey()));
			if (cur.level() < target) {
				// Grant LEVELS + the xp backing them: without xp snapped to the
				// threshold, the next award recomputes level from xp and the
				// grant silently evaporates (caught by review).
				double xp = Math.max(cur.xp(),
						LevelCurves.xpForLevel(
								SkillService.definition(e.getKey())
										.flatMap(SkillDefinition::levelCurve)
										.orElse(LevelCurves.DEFAULT_ID),
								target));
				data.setSkillProgress(e.getKey(), SkillService.clamped(e.getKey(),
						new SkillProgress(xp, target,
								Math.max(cur.highestLevel(), target), cur.protectedFloor(),
								cur.aptitude(), now, Math.max(now, cur.lastDecayCheckpoint()))));
			}
		}
		// Aptitude overrides are raise-only like levels — spec switches never
		// downgrade a recorded grade.
		for (Map.Entry<ResourceLocation, Aptitude> e : def.aptitudes().entrySet()) {
			SkillProgress cur = SkillService.ensureProgress(data, e.getKey());
			if (cur != null && e.getValue().ordinal() > cur.aptitude().ordinal()) {
				data.setSkillProgress(e.getKey(), new SkillProgress(cur.xp(), cur.level(),
						cur.highestLevel(), cur.protectedFloor(), e.getValue(),
						cur.lastMeaningfulUse(), cur.lastDecayCheckpoint()));
			}
		}
		// Protected floors are stored verbatim (raise-only): a floor above the
		// granted level is a valid decay floor — it simply cannot be undercut.
		for (Map.Entry<ResourceLocation, Integer> e : def.protectedFloors().entrySet()) {
			SkillProgress cur = SkillService.ensureProgress(data, e.getKey());
			if (cur != null && e.getValue() > cur.protectedFloor()) {
				data.setSkillProgress(e.getKey(), new SkillProgress(cur.xp(), cur.level(),
						cur.highestLevel(), e.getValue(), cur.aptitude(),
						cur.lastMeaningfulUse(), cur.lastDecayCheckpoint()));
			}
		}
		return ApplyResult.APPLIED;
	}

	/**
	 * XP multiplier contributed by the character's specialization
	 * ({@code xp_modifiers[skillId]}), or 1.0 when unset/absent/unknown.
	 */
	public static double xpModifierFor(@Nullable ResourceLocation specId, ResourceLocation skillId) {
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
	public static double decayResistanceFor(@Nullable ResourceLocation specId, ResourceLocation skillId) {
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
