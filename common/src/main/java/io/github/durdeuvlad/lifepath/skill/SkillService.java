package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
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
	private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
	private static final int WARNED_CAP = 256;
	private static final int DEFAULT_MAX_LEVEL = 100;

	private SkillService() {
	}

	/** Looks up a definition; {@code empty} + WARN-once for unknown/missing ids. */
	public static Optional<SkillDefinition> definition(@Nullable ResourceLocation skillId) {
		if (skillId == null) {
			return Optional.empty();
		}
		SkillDefinition def = LifepathContent.skills().get(skillId);
		if (def == null) {
			if (WARNED.add(skillId)) {
				if (WARNED.size() >= WARNED_CAP) {
					// Bound the set: reset at cap, then re-add so this id still dedups.
					WARNED.clear();
					WARNED.add(skillId);
					LifepathMod.LOGGER.warn(
							"unknown skill id '{}' (warn set reset at cap {})", skillId, WARNED_CAP);
				} else {
					LifepathMod.LOGGER.warn("unknown skill id '{}' (no definition loaded)", skillId);
				}
			}
			return Optional.empty();
		}
		return Optional.of(def);
	}

	/** The definition's max level, or the 100 default when no definition exists. */
	public static int maxLevel(@Nullable ResourceLocation skillId) {
		return definition(skillId).map(SkillDefinition::maxLevel).orElse(DEFAULT_MAX_LEVEL);
	}

	/**
	 * Returns a copy of {@code progress} with all invariants enforced against
	 * {@code maxLevel}. {@code highestLevel} is repaired upward to at least
	 * {@code level} (never lowered); {@code protectedFloor} is clamped into
	 * [0, maxLevel] — a floor ABOVE the current level is legal: it is a decay
	 * floor, not a level bound (decay simply cannot act while level is at or
	 * below it); negative xp/use timestamps become 0.
	 */
	public static SkillProgress clamped(SkillProgress progress, int maxLevel) {
		int level = Math.max(0, Math.min(progress.level(), Math.max(0, maxLevel)));
		int highest = Math.max(progress.highestLevel(), level);
		int floor = Math.max(0, Math.min(progress.protectedFloor(), Math.max(0, maxLevel)));
		double xp = Double.isFinite(progress.xp()) ? Math.max(0.0, progress.xp()) : 0.0;
		long lastUse = Math.max(0L, progress.lastMeaningfulUse());
		// The decay checkpoint is a monotone ledger — repairing via the 6-arg
		// ctor would rewind it to lastUse and re-open already-charged time.
		long checkpoint = Math.max(lastUse, Math.max(0L, progress.lastDecayCheckpoint()));
		return new SkillProgress(xp, level, highest, floor, progress.aptitude(),
				lastUse, checkpoint);
	}

	/** {@link #clamped} against the skill's own definition (or the 100 default). */
	public static SkillProgress clamped(ResourceLocation skillId, SkillProgress progress) {
		return clamped(progress, maxLevel(skillId));
	}

	/**
	 * Returns a copy of {@code progress} set to {@code newLevel} (clamped):
	 * {@code highestLevel} rises if the new level beats it and never falls;
	 * {@code protectedFloor} is preserved but re-clamped into [0, maxLevel].
	 */
	public static SkillProgress withLevel(SkillProgress progress, int newLevel, int maxLevel) {
		// Clamp BEFORE updating highestLevel — an out-of-range newLevel must not
		// fabricate an unattained peak (highestLevel can never be lowered again).
		int level = Math.max(0, Math.min(newLevel, Math.max(0, maxLevel)));
		return clamped(new SkillProgress(progress.xp(), level,
				Math.max(progress.highestLevel(), level),
				progress.protectedFloor(), progress.aptitude(), progress.lastMeaningfulUse(),
				progress.lastDecayCheckpoint()),
				maxLevel);
	}

	/**
	 * Read-only progress lookup: returns the stored (clamped) record for
	 * {@code skillId}, or null if absent/unknown. Unlike
	 * {@link #ensureProgress} this NEVER mutates the model — use it for
	 * inspection paths (UI, listings, decay scans) that must not create entries.
	 */
	@Nullable
	public static SkillProgress progress(PlayerCharacterData data, ResourceLocation skillId) {
		SkillProgress existing = data.skill(skillId);
		return existing == null ? null : clamped(skillId, existing);
	}

	/**
	 * Get-or-create the character's progress for {@code skillId}: returns the
	 * stored record (clamped) if present, else a fresh {@code xp=0, level=0}
	 * record with {@link Aptitude#B} which is ALSO stored on the model. Returns
	 * null when the skill id has no loaded definition (fail-safe).
	 *
	 * <p><b>Callers must intend creation.</b> Inspection-only paths must use
	 * {@link #progress} instead — every {@code ensureProgress} call on an
	 * untouched skill permanently persists (and syncs) a new map entry.
	 */
	@Nullable
	public static SkillProgress ensureProgress(PlayerCharacterData data, ResourceLocation skillId) {
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
		SkillProgress fresh = SkillProgress.fresh(Aptitude.B);
		data.setSkillProgress(skillId, fresh);
		return fresh;
	}

	/**
	 * Effective aptitude for XP/decay math (M3-1): the character's recorded
	 * grade raised to the species floor — {@code max(speciesMin, current)}.
	 * A null/unknown species means no floor. Pure lookup; writes nothing.
	 */
	public static Aptitude effectiveAptitude(@Nullable ResourceLocation speciesId,
			ResourceLocation skillId, SkillProgress progress) {
		Aptitude grade = progress.aptitude();
		if (speciesId != null) {
			var def = LifepathContent.species().get(speciesId);
			Aptitude floor = def == null ? null : def.minAptitudes().get(skillId);
			if (floor != null && floor.ordinal() > grade.ordinal()) {
				grade = floor;
			}
		}
		return grade;
	}

	/** Test hook: clears the warn-once set. Not for production use. */
	static void resetForTests() {
		WARNED.clear();
	}
}
