package com.dwurdy.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.content.OutcomeRuleDefinition;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The shipped smithing repair-cost curve (M28): anvil repairs must be
 * <em>very expensive</em> for an untrained character, cross vanilla parity
 * in the apprentice band (~Lv20–39, the pack's "vanilla equivalent ~Lv25"
 * anchor), and fall to the 1–2 level floor at master/legendary.
 *
 * <p>Decodes the real {@code outcome_rule/smithing.json} — this guards the
 * tuning contract, not a stubbed copy of it.
 */
class AnvilRepairCostCurveTest {

	private static final Path DATA = Path.of("src/main/resources/data/lifepath");

	private static OutcomeRuleDefinition loadSmithingRule() throws Exception {
		var json = JsonParser.parseString(
				Files.readString(DATA.resolve("outcome_rule/smithing.json")))
				.getAsJsonObject();
		var file = OutcomeRuleDefinition.OutcomeRuleFile.CODEC
				.parse(JsonOps.INSTANCE, json).result().orElseThrow();
		return OutcomeRuleDefinition.fromFile(
				com.dwurdy.lifepath.LifepathMod.id("smithing"), file);
	}

	private static double mult(OutcomeRuleDefinition rule, RankBands.RankBand band) {
		return rule.forBand(band).anvilCostMult();
	}

	/** Mirrors {@code AnvilMenuCostMixin}: the synced cost a band would charge
	 * for a repair whose vanilla price is {@code base} levels. */
	private static int charged(int base, double mult) {
		return Math.max(1, (int) Math.round(base * mult));
	}

	@Test
	void everyBandDeclaresAnAnvilMultiplier() {
		// An absent band decodes to IDENTITY (1.0) — silently flattening the
		// curve. The shipped file must set all seven explicitly.
		var rule = loadSmithingRuleUnchecked();
		for (RankBands.RankBand band : RankBands.RankBand.values()) {
			assertTrue(rule.bands().containsKey(band),
					"smithing rule missing anvil_cost_mult band " + band.key());
		}
	}

	@Test
	void curveIsMonotoneNonIncreasing() {
		var rule = loadSmithingRuleUnchecked();
		RankBands.RankBand[] order = RankBands.RankBand.values();
		double prev = Double.MAX_VALUE;
		for (RankBands.RankBand band : order) {
			double m = mult(rule, band);
			assertTrue(m <= prev, "anvil_cost_mult must not rise with skill: "
					+ band.key() + " is " + m);
			prev = m;
		}
	}

	@Test
	void untrainedIsPunitive() {
		var rule = loadSmithingRuleUnchecked();
		assertTrue(mult(rule, RankBands.RankBand.UNTRAINED) >= 2.5,
				"untrained repairs should be very very expensive");
	}

	@Test
	void apprenticeIsVanillaParity() {
		// Apprentice opens at Lv20 — the band covering the ~Lv25 parity point.
		var rule = loadSmithingRuleUnchecked();
		assertEquals(1.0, mult(rule, RankBands.RankBand.APPRENTICE), 1e-9,
				"apprentice repairs should cost vanilla");
	}

	@Test
	void endgameRepairsFloorAtOneOrTwoLevels() {
		var rule = loadSmithingRuleUnchecked();
		double master = mult(rule, RankBands.RankBand.MASTER);
		double legendary = mult(rule, RankBands.RankBand.LEGENDARY);
		// Ordinary repairs (1–8 vanilla levels) must land on the 1–2 floor.
		for (int base = 1; base <= 8; base++) {
			assertTrue(charged(base, master) <= 2,
					"master charged " + charged(base, master) + " for base " + base);
			assertTrue(charged(base, legendary) <= 2,
					"legendary charged " + charged(base, legendary) + " for base " + base);
		}
	}

	private static OutcomeRuleDefinition loadSmithingRuleUnchecked() {
		try {
			return loadSmithingRule();
		} catch (Exception e) {
			throw new AssertionError("shipped smithing outcome rule failed to decode", e);
		}
	}
}
