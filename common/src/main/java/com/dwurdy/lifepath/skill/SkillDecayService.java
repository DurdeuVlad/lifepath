package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.config.LifepathConfig;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.specialization.SpecializationService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Skill decay engine (M3-3, GAMEDESIGN §9-10): loss of sharpness, not
 * knowledge. Decay is LAZY — never per-tick. It is computed on demand and
 * writes back into the model at these triggers:
 *
 * <ul>
 *   <li>player login (all skills — {@link CharacterManager#initializeCharacter});</li>
 *   <li>skill award/update ({@code awardXpCore} decays that skill first);</li>
 *   <li>a low-frequency maintenance pass over online players (config
 *       {@code maintenance_minutes});</li>
 *   <li>admin {@code /lifepath character inspect}.</li>
 * </ul>
 *
 * <p>Window: {@code elapsed = now - max(lastMeaningfulUse, lastDecayCheckpoint)}
 * minus the grace period; the remainder decays the FRACTIONAL level at
 * banded per-day rates (config). Modifier composition is multiplicative:
 * {@code rate × aptitudeDecayMultiplier × (1 − specializationResistance)}.
 * Decay clamps at {@code max(protectedFloor, lowest-band bound)} and never
 * touches {@code highestLevel}. Every lazy pass advances
 * {@code lastDecayCheckpoint} to {@code now} so elapsed time is charged once.
 */
public final class SkillDecayService {
	public static final ResourceLocation DECAY_CONFIG = LifepathMod.id("decay");
	private static final long DAY_MS = 86_400_000L;
	private static final long HOUR_MS = 3_600_000L;

	private static long ticksSinceMaintenance;

	/** One decay band: levels in {@code (lower, upper]} lose {@code rate} level/day. */
	public record Band(int lower, int upper, double rate) {}

	private SkillDecayService() {}

	public static void init() {
		com.dwurdy.lifepath.platform.Platform.get().onEndServerTick(server -> {
			int interval = (int) (maintenanceMinutes() * 1200L);
			if (interval <= 0 || ++ticksSinceMaintenance < interval) {
				return;
			}
			ticksSinceMaintenance = 0;
			long now = System.currentTimeMillis();
			for (var player : server.getPlayerList().getPlayers()) {
				int changed = com.dwurdy.lifepath.perf.PerfCounters.time(
						"decay.maintenance_batch", () -> com.dwurdy.lifepath.feedback
								.FeedbackService.applyDecayWithFeedback(player, now));
				if (changed > 0) {
					CharacterManager.markDirty(player);
					CharacterManager.syncCharacter(player);
				}
			}
		});
	}

	/** Test hook: resets the maintenance tick counter. Not for production use. */
	static void resetForTests() {
		ticksSinceMaintenance = 0;
	}

	/**
	 * Lazily decays one skill and writes the result back; advances the decay
	 * checkpoint to {@code now} even when nothing decayed (no double-charge).
	 * Returns the (possibly unchanged) record. Missing definitions degrade
	 * gracefully — the record is checkpointed and returned untouched.
	 */
	public static SkillProgress applyLazy(PlayerCharacterData data, ResourceLocation skillId, long now) {
		SkillProgress cur = data.skill(skillId);
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		if (cur == null) {
			return null;
		}
		// The checkpoint is monotone: clock regressions never re-open charged
		// time, and disabled/undefined passes still CONSUME the window so a
		// later re-enable doesn't charge the whole backlog.
		long checkpoint = Math.max(now, cur.lastDecayCheckpoint());
		if (def == null || !enabled()) {
			SkillProgress stamped = new SkillProgress(cur.xp(), cur.level(),
					cur.highestLevel(), cur.protectedFloor(), cur.aptitude(),
					cur.lastMeaningfulUse(), checkpoint);
			if (!stamped.equals(cur)) {
				data.setSkillProgress(skillId, stamped);
			}
			return stamped;
		}
		double fracCur = fractionalLevel(def, cur);
		double fracRaw = decayedFractionalLevel(cur, def, data, now);
		// The floor only stops decay — it never RAISES the level; and a
		// no-decay pass must not rewrite xp (a stored xp above the curve,
		// e.g. saturated overflow, is legitimate state).
		double bounded = Math.min(fracCur, Math.max(fracRaw, cur.protectedFloor()));
		boolean decayed = bounded < fracCur - 1e-9;
		int newLevel = decayed
				? Math.max(0, Math.min((int) Math.floor(bounded), def.maxLevel()))
				: cur.level();
		double newXp = decayed ? xpForFractional(def, bounded) : cur.xp();
		SkillProgress next = new SkillProgress(newXp, newLevel,
				cur.highestLevel(), cur.protectedFloor(), cur.aptitude(),
				cur.lastMeaningfulUse(), checkpoint);
		data.setSkillProgress(skillId, next);
		return next;
	}

	/**
	 * Decays every tracked skill; returns how many records ACTUALLY lost xp
	 * or level (checkpoint-only advances don't count — callers use this for
	 * dirty/sync decisions and a checkpoint alone is safe to leave
	 * unpersisted).
	 */
	public static int applyLazyAll(PlayerCharacterData data, long now) {
		// Signature sweep lives on the same lazy triggers — dead keys would
		// otherwise accumulate in the model/NBT forever.
		data.pruneActionSignatures(now - DiminishingReturns.windowMs());
		int changed = 0;
		for (ResourceLocation id : new ArrayList<>(data.skills().keySet())) {
			SkillProgress before = data.skill(id);
			SkillProgress after = applyLazy(data, id, now);
			if (after != null
					&& (after.level() != before.level() || after.xp() != before.xp())) {
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Pure decay math: the fractional level after the decay window, BEFORE
	 * the floor clamp and xp mapping. Public for the M7-4 debug command's
	 * decay projection.
	 */
	public static double decayedFractionalLevel(SkillProgress cur, SkillDefinition def,
			PlayerCharacterData data, long now) {
		double frac = fractionalLevel(def, cur);
		// Grace is consumed ONCE per idle period (anchored on meaningful use);
		// the checkpoint marks time already charged. Subtracting grace from
		// (now − checkpoint) would re-gift the full grace every pass.
		long graceEnd = cur.lastMeaningfulUse() + (long) (graceHours() * HOUR_MS);
		long anchor = Math.max(graceEnd, cur.lastDecayCheckpoint());
		long decayMs = now - anchor;
		if (decayMs <= 0 || frac <= 0) {
			return frac;
		}
		double days = decayMs / (double) DAY_MS;
		double aptitudeMult = AptitudeTable.decayMultiplier(
				SkillService.effectiveAptitude(data.speciesId(), def.id(), cur));
		double resistance = SpecializationService.decayResistanceFor(
				data.specializationId(), def.id());
		double remaining = days * aptitudeMult * Math.max(0.0, 1.0 - resistance);
		return walkBands(frac, remaining, bands());
	}

	/** Walks {@code frac} downward through bands, spending {@code remaining} decay-days. */
	private static double walkBands(double frac, double remaining, List<Band> bands) {
		while (remaining > 0.0 && !bands.isEmpty()) {
			Band band = bandContaining(frac, bands);
			if (band == null || band.rate() <= 0.0) {
				break; // at/below the lowest band — no decay there
			}
			double span = frac - band.lower();
			double spend = span / band.rate();
			if (remaining >= spend) {
				frac = band.lower();
				remaining -= spend;
			} else {
				frac -= remaining * band.rate();
				remaining = 0;
			}
		}
		return Math.max(0.0, frac);
	}

	@Nullable
	private static Band bandContaining(double frac, List<Band> bands) {
		for (Band b : bands) {
			if (frac > b.lower() && frac <= b.upper()) {
				return b;
			}
		}
		// Above the top band decays at the top band's rate.
		return frac > bands.get(bands.size() - 1).upper() ? bands.get(bands.size() - 1) : null;
	}

	/** Current level including progress toward the next level as a fraction. */
	private static double fractionalLevel(SkillDefinition def, SkillProgress p) {
		double lo = LevelCurves.forSkill(def).xpForLevel(p.level());
		double hi = p.level() >= def.maxLevel() ? lo
				: LevelCurves.forSkill(def).xpForLevel(p.level() + 1);
		if (hi <= lo) {
			return p.level();
		}
		return p.level() + Math.min(1.0, Math.max(0.0, (p.xp() - lo) / (hi - lo)));
	}

	/** Maps a fractional level back to xp on the skill's curve. */
	private static double xpForFractional(SkillDefinition def, double frac) {
		int level = (int) Math.floor(frac);
		double lo = LevelCurves.forSkill(def).xpForLevel(level);
		if (level >= def.maxLevel()) {
			return lo;
		}
		double hi = LevelCurves.forSkill(def).xpForLevel(level + 1);
		return lo + (frac - level) * (hi - lo);
	}

	// ---- config (decay.toml) ----

	public static boolean enabled() {
		return ((Boolean) LifepathConfig.getOrDefault(DECAY_CONFIG, "enabled", Boolean.TRUE));
	}

	public static double graceHours() {
		return ((Number) LifepathConfig.getOrDefault(DECAY_CONFIG, "grace_hours", 48.0)).doubleValue();
	}

	public static double maintenanceMinutes() {
		return ((Number) LifepathConfig.getOrDefault(DECAY_CONFIG, "maintenance_minutes", 60.0)).doubleValue();
	}

	/**
	 * The configured bands, ascending. Malformed/missing entries fall back to
	 * §9 defaults; a non-monotone {@code band_i_upper} collapses to the
	 * previous bound (a degenerate, never-matching band) rather than
	 * overlapping earlier bands.
	 */
	static List<Band> bands() {
		int[] defaultsUpper = {25, 50, 75, 90, 100};
		double[] defaultsRate = {0.0, 0.05, 0.10, 0.20, 0.35};
		List<Band> bands = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			int upper = ((Number) LifepathConfig.getOrDefault(
					DECAY_CONFIG, "band_" + (i + 1) + "_upper", defaultsUpper[i])).intValue();
			double rate = ((Number) LifepathConfig.getOrDefault(
					DECAY_CONFIG, "band_" + (i + 1) + "_rate", defaultsRate[i])).doubleValue();
			int lower = bands.isEmpty() ? 0 : bands.get(bands.size() - 1).upper();
			bands.add(new Band(lower, Math.max(upper, lower), i == 0 ? Math.max(0.0, rate) : rate));
		}
		return bands;
	}
}
