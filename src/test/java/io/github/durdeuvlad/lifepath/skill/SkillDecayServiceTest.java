package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.LevelCurveDefinition;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Decay math boundary tests (M3-3). All times are synthetic — the service
 * takes {@code now} explicitly so offline-elapsed behavior is deterministic.
 */
class SkillDecayServiceTest {
	private static final Identifier SKILL = Identifier.of("lifepath", "decaytest");
	private static final Identifier CURVE = Identifier.of("lifepath", "decay_curve");
	private static final long DAY = 86_400_000L;
	private static final long GRACE = 48L * 3_600_000L;
	private static final long T0 = 1_700_000_000_000L;

	@BeforeEach
	void setUp() {
		LifepathContent.skills().clear();
		LifepathContent.levelCurves().clear();
		LifepathContent.specializations().clear();
		SkillDecayService.resetForTests();
		// xpForLevel(n) = 10n on this curve (linear) — easy level math.
		List<Double> t = new ArrayList<>();
		for (int i = 0; i <= 101; i++) {
			t.add(10.0 * i);
		}
		LifepathContent.levelCurves().register(CURVE, new LevelCurveDefinition(CURVE, t));
		LifepathContent.skills().register(SKILL, new SkillDefinition(SKILL, "D",
				SkillDefinition.Category.GATHERING, 100, Optional.of(CURVE), List.of(),
				List.of(), Optional.empty()));
	}

	/** level L exactly: xp at the level threshold, last-use {@code use}. */
	private static SkillProgress atLevel(int level, long use) {
		return new SkillProgress(10.0 * level, level, level, 0, Aptitude.B, use);
	}

	@Test
	void noDecayInsideGracePeriod() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(SKILL, atLevel(60, T0));
		SkillProgress p = SkillDecayService.applyLazy(data, SKILL, T0 + GRACE - 1);
		assertEquals(60, p.level());
		assertEquals(600.0, p.xp());
	}

	@Test
	void bandOneNeverDecays() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(SKILL, atLevel(24, T0));
		SkillProgress p = SkillDecayService.applyLazy(data, SKILL, T0 + GRACE + 400 * DAY);
		assertEquals(24, p.level(), "0–25 band has rate 0 — never decays");
		assertEquals(240.0, p.xp());
	}

	@Test
	void singleBandDecay() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(SKILL, atLevel(40, T0));
		// 10 days past grace in the 26–50 band at 0.05/day → −0.5 level.
		SkillProgress p = SkillDecayService.applyLazy(data, SKILL, T0 + GRACE + 10 * DAY);
		assertEquals(39, p.level());
		assertEquals(395.0, p.xp(), 0.001);
		assertEquals(40, p.highestLevel(), "highestLevel is never reduced");
	}

	@Test
	void bandCrossingUsesEachBandsRate() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(SKILL, atLevel(52, T0));
		// 30 days: 52→50 in band 51–75 (2 levels @ 0.10 = 20 days), then the
		// remaining 10 days in band 26–50 @ 0.05 → 0.5 level → 49.5.
		SkillProgress p = SkillDecayService.applyLazy(data, SKILL, T0 + GRACE + 30 * DAY);
		assertEquals(49, p.level());
		assertEquals(495.0, p.xp(), 0.001);
	}

	@Test
	void decayStopsAtProtectedFloor() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		SkillProgress withFloor = new SkillProgress(300.0, 30, 30, 29, Aptitude.B, T0);
		data.setSkillProgress(SKILL, withFloor);
		// 200 days would otherwise walk it down to the 0–25 band — the floor
		// stops it at exactly 29.
		SkillProgress p = SkillDecayService.applyLazy(data, SKILL, T0 + GRACE + 200 * DAY);
		assertEquals(29, p.level());
		assertEquals(290.0, p.xp(), 0.001);
	}

	@Test
	void checkpointAdvancesAndNeverDoubleCharges() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(SKILL, atLevel(60, T0));
		long now = T0 + GRACE + 10 * DAY;
		SkillProgress first = SkillDecayService.applyLazy(data, SKILL, now);
		assertEquals(now, first.lastDecayCheckpoint());
		// A second pass at the same instant must be a no-op for decay.
		SkillProgress second = SkillDecayService.applyLazy(data, SKILL, now);
		assertEquals(first.xp(), second.xp());
		assertEquals(first.level(), second.level());
	}

	@Test
	void aptitudeAndSpecResistanceCompose() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		// D grade decays ×1.25; a spec with 60% resistance on this skill halves it.
		io.github.durdeuvlad.lifepath.content.SpecializationDefinition spec =
				new io.github.durdeuvlad.lifepath.content.SpecializationDefinition(
						Identifier.of("lifepath", "s"), "S", Map.of(), Map.of(), Map.of(),
						Map.of(SKILL, 0.6), Map.of(), List.of());
		LifepathContent.specializations().register(spec.id(), spec);
		data.setSpecializationId(spec.id());
		data.setSkillProgress(SKILL, new SkillProgress(600.0, 60, 60, 0, Aptitude.D, T0));
		// Level 60 sits in band 51–75 (0.10/day). Decay-days: 10 × 1.25 (D)
		// × (1−0.6) = 5 → 5 × 0.10 = 0.5 level → fractional 59.5.
		SkillProgress p = SkillDecayService.applyLazy(data, SKILL, T0 + GRACE + 10 * DAY);
		assertEquals(59.5, p.xp() / 10.0, 0.001);
	}

	@Test
	void freshSkillAndMissingDefinitionDegradeGracefully() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		// Untracked skill → null, no throw.
		assertEquals(null, SkillDecayService.applyLazy(data, SKILL, T0));
		// Skill with no definition: checkpointed untouched.
		Identifier ghost = Identifier.of("lifepath", "ghost");
		data.setSkillProgress(ghost, atLevel(50, T0));
		SkillProgress p = SkillDecayService.applyLazy(data, ghost, T0 + GRACE + 30 * DAY);
		assertEquals(50, p.level());
	}
}
