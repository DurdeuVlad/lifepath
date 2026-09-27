package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.character.IdentitySummary;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Identifier;
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
		cards.sort(Comparator.comparing(c -> c.display().name()));
		return new SkillsSummaryPayload(cards);
	}

	private static SkillCard card(Identifier id, SkillDefinition def,
			PlayerCharacterData data) {
		SkillProgress prog = data.skill(id);
		int level = prog != null ? prog.level() : 0;
		int floor = prog != null ? prog.protectedFloor() : 0;

		Identifier curveId = def.levelCurve().orElse(LevelCurves.DEFAULT_ID);
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
		List<String> bonuses = new ArrayList<>();
		for (SkillDefinition.Milestone m : def.milestones()) {
			if (m.level() <= level) {
				for (Identifier ref : m.effectRefs()) {
					bonuses.add(IdentitySummary.displayName(ref));
				}
			} else if (nextLevel == 0 || m.level() < nextLevel) {
				nextLevel = m.level();
				nextText = m.descriptionKey();
			}
		}

		return new SkillCard(id.toString(),
				new SkillCard.Display(def.displayName(), def.description(),
						RankBands.bandFor(level).key(), apt.name(), def.improveHint()),
				new SkillCard.Progress(level, xpIn, xpNeed, floor, graceEnd),
				new SkillCard.Details(nextLevel, nextText, List.copyOf(bonuses)));
	}

	/** Species minAptitudes floor, else the neutral default grade. */
	private static Aptitude speciesFloor(@Nullable Identifier speciesId, Identifier skillId) {
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
