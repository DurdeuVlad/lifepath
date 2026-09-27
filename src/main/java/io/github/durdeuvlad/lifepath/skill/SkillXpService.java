package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.SkillEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The authoritative XP service (TIMELINE §5, M2-2). <b>Server-side only</b>:
 * every API takes a {@link ServerPlayerEntity} or the model — no C2S "award
 * me XP" packet exists anywhere in the mod, so clients physically cannot
 * trigger it.
 *
 * <p>Award pipeline ({@link #awardXp}): validate (known skill, finite amount
 * &gt; 0, below max) → apply the ordered {@link XpModifier} pipeline → add XP →
 * recompute level from the skill's {@link LevelCurves level curve} → update
 * {@code highestLevel}/{@code lastMeaningfulUse} → mark dirty + re-sync →
 * fire {@link SkillEvents#LEVEL_UP} when the level rose.
 *
 * <p><b>Modifier seam:</b> {@link #registerModifier} appends to an ordered
 * list applied in registration order. The built-in
 * {@code lifepath:global_multiplier} (config {@code skills.toml
 * global_xp_multiplier}) is registered first; M3 lands aptitude,
 * specialization, and diminishing-returns modifiers behind it — no refactor
 * needed. Modifier contract: deterministic, side-effect-free, returns the
 * adjusted amount; {@code <=0} or NaN suppresses the gain, +Inf saturates, and
 * a thrown exception is isolated (logged ERROR, treated as identity).
 *
 * <p><b>Threading:</b> server main thread only — same contract as
 * {@code CharacterManager}. Registration is not thread-safe; do it during init.
 *
 * <p><b>{@code lastMeaningfulUse}</b> uses wall-clock millis — it records real
 * elapsed time for later decay/inactivity systems, not tick counts, so a
 * backwards clock step merely shifts an idle-start estimate.
 */
public final class SkillXpService {
	private SkillXpService() {
	}

	/** Outcome of an award/set operation, returned for producers and tests. */
	public record XpResult(int levelsGained, int oldLevel, int newLevel,
			double xpBefore, double xpAfter, boolean applied) {
	}

	private static final Map<Identifier, XpModifier> MODIFIERS = new LinkedHashMap<>();
	private static final Identifier GLOBAL_MULTIPLIER = LifepathMod.id("global_multiplier");
	private static final Identifier SKILLS_CONFIG = LifepathMod.id("skills");
	private static boolean initialized;

	/** Registers the built-in config-multiplier modifier. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		registerModifier(GLOBAL_MULTIPLIER, (ctx, amount) ->
				amount * ((Number) LifepathConfig.getOrDefault(
						SKILLS_CONFIG, "global_xp_multiplier", 1.0)).doubleValue());
		// Per-skill multiplier: config key "<skill_path>_xp_multiplier" (unknown
		// skills get 1.0 via getOrDefault). Runs after the global multiplier.
		registerModifier(LifepathMod.id("per_skill_multiplier"), (ctx, amount) ->
				amount * ((Number) LifepathConfig.getOrDefault(SKILLS_CONFIG,
						ctx.skillId().getPath() + "_xp_multiplier", 1.0)).doubleValue());
		// Aptitude multiplier (M3-1): effective grade = max(species floor,
		// recorded). Runs last of the built-ins. Reads speciesId from the
		// award's own data model — applies on data-only paths too and keeps
		// the modifier side-effect-free (no lazy attachment load).
		registerModifier(LifepathMod.id("aptitude_multiplier"), (ctx, amount) ->
				amount * AptitudeTable.xpMultiplier(SkillService.effectiveAptitude(
						ctx.speciesId(), ctx.skillId(), ctx.progress())));
		// Specialization multiplier (M3-2): spec xp_modifiers[skillId], read
		// at award time off the data model — reload-safe.
		registerModifier(LifepathMod.id("specialization_multiplier"), (ctx, amount) ->
				amount * io.github.durdeuvlad.lifepath.specialization.SpecializationService
						.xpModifierFor(ctx.specializationId(), ctx.skillId()));
		// Diminishing returns (M3-4): runs LAST so the spam discount applies to
		// the fully-multiplied amount; records the action signature itself.
		registerModifier(DiminishingReturns.MODIFIER_ID, DiminishingReturns::apply);
	}

	/** Appends {@code modifier} to the pipeline; later registrations run later. */
	public static void registerModifier(Identifier id, XpModifier modifier) {
		if (MODIFIERS.putIfAbsent(id, modifier) != null) {
			LifepathMod.LOGGER.warn("duplicate xp modifier id '{}' — keeping the first", id);
		}
	}

	/** Ordered pipeline ids — the "modifiers in effect" list for {@code debug character}. */
	public static List<Identifier> modifierIds() {
		return List.copyOf(MODIFIERS.keySet());
	}

	/**
	 * Awards XP to {@code player}. Returns the {@link XpResult}; {@code applied}
	 * is false when validation rejected the award (no mutation happened).
	 */
	public static XpResult awardXp(ServerPlayerEntity player, Identifier skillId,
			double amount, ActivityEvent source) {
		// Validate BEFORE touching the cache — a rejected award must not
		// lazy-load (and leak) a character entry.
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		if (def == null || !Double.isFinite(amount) || amount <= 0) {
			return new XpResult(0, 0, 0, 0, 0, false);
		}
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		XpResult result = awardXpCore(data, skillId, amount, source, player);
		if (result.applied()) {
			CharacterManager.changed(player);
			if (result.levelsGained() > 0) {
				SkillEvents.LEVEL_UP.invoker().onLevelUp(player, skillId,
						result.oldLevel(), result.newLevel(), source);
			}
		}
		return result;
	}

	/**
	 * Data-path award for engine services (ability actions, admin tooling)
	 * that operate on a character model directly. Mutates {@code data};
	 * callers mark dirty/sync when a player is attached.
	 */
	public static XpResult awardXp(PlayerCharacterData data, Identifier skillId,
			double amount, ActivityEvent source) {
		return awardXpCore(data, skillId, amount, source, null);
	}

	/** Data-only award path used by {@link #awardXp} and unit tests (no player). */
	static XpResult awardXpCore(PlayerCharacterData data, Identifier skillId,
			double amount, ActivityEvent source) {
		return awardXpCore(data, skillId, amount, source, null);
	}

	private static XpResult awardXpCore(PlayerCharacterData data, Identifier skillId,
			double amount, ActivityEvent source, @Nullable ServerPlayerEntity player) {
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		SkillProgress existing = SkillService.progress(data, skillId);
		if (def == null || !Double.isFinite(amount) || amount <= 0) {
			// Rejected: still report real current values when progress exists.
			return existing == null
					? new XpResult(0, 0, 0, 0, 0, false)
					: new XpResult(0, existing.level(), existing.level(),
							existing.xp(), existing.xp(), false);
		}
		// Lazy decay (M3-3): charge the elapsed window for THIS skill before
		// the award stamps a fresh meaningful-use timestamp.
		SkillDecayService.applyLazy(data, skillId, System.currentTimeMillis());
		SkillProgress current = SkillService.ensureProgress(data, skillId);
		if (current == null) {
			return new XpResult(0, 0, 0, 0, 0, false);
		}
		long now = System.currentTimeMillis();
		if (current.level() >= def.maxLevel()) {
			// At cap: practice still counts as meaningful use (decay reads this
			// timestamp), but no XP accrues and no level can change.
			data.setSkillProgress(skillId, SkillService.clamped(
					new SkillProgress(current.xp(), current.level(), current.highestLevel(),
							current.protectedFloor(), current.aptitude(), now,
							Math.max(now, current.lastDecayCheckpoint())), def.maxLevel()));
			return new XpResult(0, current.level(), current.level(),
					current.xp(), current.xp(), true);
		}
		XpModifier.XpContext ctx = new XpModifier.XpContext(player, skillId, current,
				source, data.speciesId(), data.specializationId(), data);
		double modified = amount;
		for (Map.Entry<Identifier, XpModifier> entry : MODIFIERS.entrySet()) {
			try {
				double out = entry.getValue().apply(ctx, modified);
				if (Double.isNaN(out)) {
					modified = 0;
					break;
				}
				// +Inf clamps to MAX_VALUE rather than wiping the award; <=0 suppresses.
				modified = out == Double.POSITIVE_INFINITY ? Double.MAX_VALUE
						: out == Double.NEGATIVE_INFINITY ? 0.0 : Math.max(0.0, out);
			} catch (Exception e) {
				// A throwing modifier is a bug, not a service failure: identity + ERROR.
				LifepathMod.LOGGER.error("xp modifier {} threw; treating as identity",
						entry.getKey(), e);
			}
		}
		double sum = current.xp() + Math.max(0.0, modified);
		// Saturate: an overflowing sum is "maxed out", never a wrapped/NaN wipe.
		double newXp = Double.isFinite(sum) ? Math.max(0.0, sum) : Double.MAX_VALUE;
		int newLevel = Math.min(def.maxLevel(), LevelCurves.levelForSkill(def, newXp));
		SkillProgress next = SkillService.clamped(
				new SkillProgress(newXp, newLevel, Math.max(current.highestLevel(), newLevel),
						current.protectedFloor(), current.aptitude(), now,
						Math.max(now, current.lastDecayCheckpoint())), def.maxLevel());
		data.setSkillProgress(skillId, next);
		return new XpResult(Math.max(0, newLevel - current.level()), current.level(),
				newLevel, current.xp(), next.xp(), true);
	}

	/** Admin/setter path: absolute XP, clamped, level recomputed. Fires LEVEL_UP on increase. */
	public static XpResult setXp(ServerPlayerEntity player, Identifier skillId,
			double xp, ActivityEvent source) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		XpResult result = setXpCore(data, skillId, xp);
		if (result.applied()) {
			CharacterManager.changed(player);
			if (result.levelsGained() > 0) {
				SkillEvents.LEVEL_UP.invoker().onLevelUp(player, skillId,
						result.oldLevel(), result.newLevel(), source);
			}
		}
		return result;
	}

	/** Data-only {@link #setXp} core — no dirty marking, sync, or event fire. */
	static XpResult setXpCore(PlayerCharacterData data, Identifier skillId, double xp) {
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		if (def == null || !Double.isFinite(xp) || xp < 0) {
			return new XpResult(0, 0, 0, 0, 0, false);
		}
		SkillProgress current = SkillService.ensureProgress(data, skillId);
		if (current == null) {
			return new XpResult(0, 0, 0, 0, 0, false);
		}
		int newLevel = Math.min(def.maxLevel(), LevelCurves.levelForSkill(def, xp));
		SkillProgress next = SkillService.clamped(
				new SkillProgress(xp, newLevel, Math.max(current.highestLevel(), newLevel),
						current.protectedFloor(), current.aptitude(), System.currentTimeMillis(),
						Math.max(System.currentTimeMillis(), current.lastDecayCheckpoint())),
				def.maxLevel());
		data.setSkillProgress(skillId, next);
		return new XpResult(Math.max(0, newLevel - current.level()), current.level(),
				newLevel, current.xp(), xp, true);
	}

	/** Admin/setter path: absolute level (clamped); XP snaps to the level's threshold. */
	public static XpResult setLevel(ServerPlayerEntity player, Identifier skillId,
			int level, ActivityEvent source) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		XpResult result = setLevelCore(data, skillId, level);
		if (result.applied()) {
			CharacterManager.changed(player);
			if (result.levelsGained() > 0) {
				SkillEvents.LEVEL_UP.invoker().onLevelUp(player, skillId,
						result.oldLevel(), result.newLevel(), source);
			}
		}
		return result;
	}

	/** Data-only {@link #setLevel} core — no dirty marking, sync, or event fire. */
	static XpResult setLevelCore(PlayerCharacterData data, Identifier skillId, int level) {
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		if (def == null || level < 0) {
			return new XpResult(0, 0, 0, 0, 0, false);
		}
		double xp = LevelCurves.xpForLevel(def.levelCurve().orElse(LevelCurves.DEFAULT_ID),
				Math.min(level, def.maxLevel()));
		return setXpCore(data, skillId, xp);
	}

	public static int getLevel(PlayerCharacterData data, Identifier skillId) {
		SkillProgress p = SkillService.progress(data, skillId);
		return p == null ? 0 : p.level();
	}

	@Nullable
	public static SkillProgress getProgress(PlayerCharacterData data, Identifier skillId) {
		return SkillService.progress(data, skillId);
	}

	/** Test hook: resets modifier pipeline + init flag. Not for production use. */
	static void resetForTests() {
		MODIFIERS.clear();
		initialized = false;
	}
}
