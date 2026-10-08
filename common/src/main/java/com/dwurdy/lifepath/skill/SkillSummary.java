package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.character.IdentitySummary;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.OutcomeRuleDefinition;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.network.s2c.IdentitySummaryPayload;
import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload;
import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves every registered skill + the player's progress into display cards
 * for {@link SkillsSummaryPayload} (M6-2). Reads registries server-side; the
 * client renders resolved strings/numbers only. Missing progress is level 0
 * — the card still shows so a fresh player can read "how to improve".
 */
public final class SkillSummary {
	private SkillSummary() {
	}

	/** {@code graceEndsEpochMs} sentinel: decay is disabled in config. */
	public static final long DECAY_DISABLED = -1L;
	/** {@code graceEndsEpochMs} sentinel: the skill has never been practiced. */
	public static final long NEVER_PRACTICED = 0L;

	public static SkillsSummaryPayload build(PlayerCharacterData data, long nowMillis) {
		List<SkillCard> cards = new ArrayList<>();
		for (var entry : LifepathContent.skills().all().entrySet()) {
			cards.add(card(entry.getKey(), entry.getValue(), data));
		}
		cards.sort(Comparator.comparing(c -> c.display().name().getString()));
		return new SkillsSummaryPayload(cards);
	}

	private static SkillCard card(ResourceLocation id, SkillDefinition def,
			PlayerCharacterData data) {
		SkillProgress prog = data.skill(id);
		int level = prog != null ? prog.level() : 0;
		int floor = prog != null ? prog.protectedFloor() : 0;

		ResourceLocation curveId = def.levelCurve().orElse(LevelCurves.DEFAULT_ID);
		double floorXp = LevelCurves.xpForLevel(curveId, level);
		double nextXp = level >= def.maxLevel()
				? floorXp : LevelCurves.xpForLevel(curveId, level + 1);
		double xpIn = prog != null ? Math.max(0, prog.xp() - floorXp) : 0;
		double xpNeed = Math.max(0, nextXp - floorXp);

		Aptitude apt = prog != null
				? SkillService.effectiveAptitude(data.speciesId(), id, prog)
				: speciesFloor(data.speciesId(), id);

		long graceEnd = graceEnd(prog, data);

		int nextLevel = 0;
		String nextText = "";
		List<IdentitySummaryPayload.Entry> bonuses = new ArrayList<>();
		List<IdentitySummaryPayload.Entry> nextEffects = new ArrayList<>();
		List<SkillCard.MilestoneRow> roadmap = new ArrayList<>();
		for (SkillDefinition.Milestone m : def.milestones()) {
			List<IdentitySummaryPayload.Entry> effects = new ArrayList<>();
			for (ResourceLocation ref : m.effectRefs()) {
				effects.add(IdentitySummary.entry(ref));
			}
			// M26: every milestone becomes a roadmap row — the client ladder
			// shows reached vs. upcoming with the XP the level costs.
			roadmap.add(new SkillCard.MilestoneRow(m.level(), m.descriptionKey(),
					LevelCurves.xpForLevel(curveId, m.level()),
					List.copyOf(effects)));
			if (m.level() <= level) {
				bonuses.addAll(effects);
			} else if (nextLevel == 0 || m.level() < nextLevel) {
				nextLevel = m.level();
				nextText = m.descriptionKey();
				nextEffects.clear();
				nextEffects.addAll(effects);
			}
		}
		roadmap.sort(Comparator.comparingInt(SkillCard.MilestoneRow::level));

		return new SkillCard(id.toString(),
				new SkillCard.Display(
						IdentitySummary.keyedText(id, "skill", "name",
								def.displayName()),
						IdentitySummary.keyedText(id, "skill", "description",
								def.description()),
						RankBands.bandFor(level).key(), apt.name(),
						IdentitySummary.keyedText(id, "skill", "improve_hint",
								def.improveHint()),
						def.icon().map(ResourceLocation::toString).orElse("")),
				new SkillCard.Progress(level, xpIn, xpNeed,
						prog != null ? prog.xp() : 0.0, floor, graceEnd),
				new SkillCard.Details(nextLevel, nextText, List.copyOf(bonuses),
						List.copyOf(nextEffects), List.copyOf(roadmap),
						bandStats(id), bandThresholds()));
	}

	/**
	 * Per-band outcome odds for the skill's {@code outcome_rule} (M26). One
	 * row per rank band, in ascending level order; empty when the skill has
	 * no rule — athletics/defence genuinely have no odds to show.
	 */
	private static List<SkillCard.BandStat> bandStats(ResourceLocation skillId) {
		OutcomeRuleDefinition rule = LifepathContent.outcomeRules().all()
				.values().stream()
				.filter(r -> r.skill().equals(skillId))
				.findFirst().orElse(null);
		if (rule == null) {
			return List.of();
		}
		int[] thresholds = RankBands.thresholds();
		RankBands.RankBand[] order = RankBands.RankBand.values();
		List<SkillCard.BandStat> out = new ArrayList<>(order.length);
		for (int i = 0; i < order.length; i++) {
			OutcomeRuleDefinition.BandModifiers m = rule.forBand(order[i]);
			out.add(new SkillCard.BandStat(order[i].key(), thresholds[i],
					m.outputCountMult(), m.failureChance(), m.failureCountMult(),
					m.qualityTier().orElse(""), m.signItems(),
					m.anvilCostMult(), m.junkUpgradeChance()));
		}
		return List.copyOf(out);
	}

	/** Server-side band thresholds — the client names bands off these, not
	 * its own (possibly divergent) config. */
	private static List<Integer> bandThresholds() {
		int[] t = RankBands.thresholds();
		List<Integer> out = new ArrayList<>(t.length);
		for (int v : t) {
			out.add(v);
		}
		return List.copyOf(out);
	}

	/** Species minAptitudes floor, else the neutral default grade. */
	private static Aptitude speciesFloor(@Nullable ResourceLocation speciesId, ResourceLocation skillId) {
		if (speciesId != null) {
			var def = LifepathContent.species().get(speciesId);
			Aptitude floor = def == null ? null : def.minAptitudes().get(skillId);
			if (floor != null) {
				return floor;
			}
		}
		return Aptitude.B;
	}

	/**
	 * Decay inputs for the card: {@link #DECAY_DISABLED} when the decay engine
	 * is off; {@link #NEVER_PRACTICED} when the skill was never used; else the
	 * epoch at which the post-use grace period expires (protection window).
	 */
	private static long graceEnd(@Nullable SkillProgress prog, PlayerCharacterData data) {
		if (prog == null || prog.lastMeaningfulUse() <= 0) {
			return NEVER_PRACTICED;
		}
		if (!SkillDecayService.enabled()) {
			return DECAY_DISABLED;
		}
		return prog.lastMeaningfulUse()
				+ (long) (SkillDecayService.graceHours() * 3_600_000.0);
	}
}
