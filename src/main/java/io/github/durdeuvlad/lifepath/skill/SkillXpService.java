package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.SkillEvents;
import java.util.LinkedHashMap;
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
 * needed.
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
	}

	/** Appends {@code modifier} to the pipeline; later registrations run later. */
	public static void registerModifier(Identifier id, XpModifier modifier) {
		if (MODIFIERS.putIfAbsent(id, modifier) != null) {
			LifepathMod.LOGGER.warn("duplicate xp modifier id '{}' — keeping the first", id);
		}
	}

	/**
	 * Awards XP to {@code player}. Returns the {@link XpResult}; {@code applied}
	 * is false when validation rejected the award (no mutation happened).
	 */
	public static XpResult awardXp(ServerPlayerEntity player, Identifier skillId,
			double amount, ActivityEvent source) {
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

	/** Data-only award path used by {@link #awardXp} and unit tests (no player). */
	static XpResult awardXpCore(PlayerCharacterData data, Identifier skillId,
			double amount, ActivityEvent source) {
		return awardXpCore(data, skillId, amount, source, null);
	}

	private static XpResult awardXpCore(PlayerCharacterData data, Identifier skillId,
			double amount, ActivityEvent source, @Nullable ServerPlayerEntity player) {
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		if (def == null || !Double.isFinite(amount) || amount <= 0) {
			return new XpResult(0, 0, 0, 0, 0, false);
		}
		SkillProgress current = SkillService.ensureProgress(data, skillId);
		if (current == null || current.level() >= def.maxLevel()) {
			return new XpResult(0, current == null ? 0 : current.level(),
					current == null ? 0 : current.level(), current == null ? 0 : current.xp(),
					current == null ? 0 : current.xp(), false);
		}
		XpModifier.XpContext ctx = new XpModifier.XpContext(player, skillId, current, source);
		double modified = amount;
		for (XpModifier modifier : MODIFIERS.values()) {
			modified = modifier.apply(ctx, modified);
			if (!Double.isFinite(modified) || modified < 0) {
				modified = 0;
				break;
			}
		}
		long now = System.currentTimeMillis();
		double newXp = Math.max(0.0, current.xp() + Math.max(0.0, modified));
		int newLevel = Math.min(def.maxLevel(), LevelCurves.levelForSkill(def, newXp));
		SkillProgress next = SkillService.clamped(
				new SkillProgress(newXp, newLevel, Math.max(current.highestLevel(), newLevel),
						current.protectedFloor(), current.aptitude(), now), def.maxLevel());
		data.setSkillProgress(skillId, next);
		return new XpResult(Math.max(0, newLevel - current.level()), current.level(),
				newLevel, current.xp(), newXp, true);
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
						current.protectedFloor(), current.aptitude(), System.currentTimeMillis()),
				def.maxLevel());
		data.setSkillProgress(skillId, next);
		return new XpResult(Math.max(0, newLevel - current.level()), current.level(),
				newLevel, current.xp(), xp, true);
	}

	/** Admin/setter path: absolute level (clamped); XP snaps to the level's threshold. */
	public static XpResult setLevel(ServerPlayerEntity player, Identifier skillId,
			int level, ActivityEvent source) {
		SkillDefinition def = SkillService.definition(skillId).orElse(null);
		if (def == null || level < 0) {
			return new XpResult(0, 0, 0, 0, 0, false);
		}
		double xp = LevelCurves.xpForLevel(def.levelCurve().orElse(LevelCurves.DEFAULT_ID),
				Math.min(level, def.maxLevel()));
		return setXp(player, skillId, xp, source);
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
