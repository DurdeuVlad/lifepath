package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Skill-domain service: safe definition lookup and {@link SkillProgress}
 * invariants. Server-side, stateless apart from the warn-once set.
 *
 * <p><b>Graceful degradation:</b> an unknown skill id (deleted content,
 * typo'd reference) yields {@link Optional#empty()} + one WARN per id — never
 * a crash, never log spam. Existing progress for a now-deleted skill stays in
 * the save until the sanitize pass drops it with WARN on next load.
 *
 * <p><b>Invariants enforced here:</b> {@code level ∈ [0, maxLevel]};
 * {@code highestLevel} never decreases; {@code protectedFloor ∈ [0, level]};
 * {@code xp ≥ 0}; {@code lastMeaningfulUse ≥ 0}. All mutators return clamped
 * COPIES — records stay immutable.
 */
public final class SkillService {
	private static final Set<Identifier> WARNED = ConcurrentHashMap.newKeySet();
	private static final int DEFAULT_MAX_LEVEL = 100;

	private SkillService() {
	}

	/** Looks up a definition; {@code empty} + WARN-once for unknown/missing ids. */
	public static Optional<SkillDefinition> definition(@Nullable Identifier skillId) {
		if (skillId == null) {
			return Optional.empty();
		}
		SkillDefinition def = LifepathContent.skills().get(skillId);
		if (def == null) {
			if (WARNED.add(skillId)) {
				LifepathMod.LOGGER.warn("unknown skill id '{}' (no definition loaded)", skillId);
			}
			return Optional.empty();
		}
		return Optional.of(def);
	}

	/** The definition's max level, or the 100 default when no definition exists. */
	public static int maxLevel(@Nullable Identifier skillId) {
		return definition(skillId).map(SkillDefinition::maxLevel).orElse(DEFAULT_MAX_LEVEL);
	}

	/**
	 * Returns a copy of {@code progress} with all invariants enforced against
	 * {@code maxLevel}. {@code highestLevel} is repaired upward to at least
	 * {@code level} (never lowered); {@code protectedFloor} is clamped into
	 * [0, level]; negative xp/use timestamps become 0.
	 */
	public static SkillProgress clamped(SkillProgress progress, int maxLevel) {
		int level = Math.max(0, Math.min(progress.level(), maxLevel));
		int highest = Math.max(progress.highestLevel(), level);
		int floor = Math.max(0, Math.min(progress.protectedFloor(), level));
		double xp = Math.max(0.0, progress.xp());
		long lastUse = Math.max(0L, progress.lastMeaningfulUse());
		return new SkillProgress(xp, level, highest, floor, progress.aptitude(), lastUse);
	}

	/** {@link #clamped} against the skill's own definition (or the 100 default). */
	public static SkillProgress clamped(Identifier skillId, SkillProgress progress) {
		return clamped(progress, maxLevel(skillId));
	}

	/**
	 * Returns a copy of {@code progress} set to {@code newLevel} (clamped):
	 * {@code highestLevel} rises if the new level beats it and never falls;
	 * {@code protectedFloor} is preserved but re-clamped into [0, level].
	 */
	public static SkillProgress withLevel(SkillProgress progress, int newLevel, int maxLevel) {
		return clamped(new SkillProgress(progress.xp(), newLevel,
				Math.max(progress.highestLevel(), newLevel),
				progress.protectedFloor(), progress.aptitude(), progress.lastMeaningfulUse()),
				maxLevel);
	}

	/**
	 * Get-or-create the character's progress for {@code skillId}: returns the
	 * stored record (clamped) if present, else a fresh {@code xp=0, level=0}
	 * record with {@link Aptitude#C} which is ALSO stored on the model. Returns
	 * null when the skill id has no loaded definition (fail-safe).
	 */
	@Nullable
	public static SkillProgress ensureProgress(PlayerCharacterData data, Identifier skillId) {
		if (definition(skillId).isEmpty()) {
			return null;
		}
		SkillProgress existing = data.skill(skillId);
		if (existing != null) {
			SkillProgress fixed = clamped(skillId, existing);
			if (!fixed.equals(existing)) {
				data.setSkillProgress(skillId, fixed);
			}
			return fixed;
		}
		SkillProgress fresh = SkillProgress.fresh(Aptitude.C);
		data.setSkillProgress(skillId, fresh);
		return fresh;
	}

	/** Test hook: clears the warn-once set. Not for production use. */
	static void resetForTests() {
		WARNED.clear();
	}
}
