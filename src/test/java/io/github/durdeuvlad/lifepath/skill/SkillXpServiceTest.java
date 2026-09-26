package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.LevelCurveDefinition;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the data-only core ({@code awardXpCore}) — the player-facing wrapper
 * needs a live {@code ServerPlayerEntity} and is exercised in-game. What MUST
 * be proven here: validation, modifier order/semantics, level recompute,
 * invariants, and the "no client award path" shape (the API takes server types).
 */
class SkillXpServiceTest {
	private static final Identifier SKILL = Identifier.of("lifepath", "mining");
	private static final Identifier CURVE = Identifier.of("lifepath", "test_curve");
	private static final ActivityEvent SRC = ActivityEvent.of(Identifier.of("lifepath", "test"), null);

	@BeforeEach
	void setUp() {
		LifepathContent.skills().clear();
		LifepathContent.levelCurves().clear();
		SkillXpService.resetForTests();
		SkillService.resetForTests();
		LevelCurves.resetForTests();
	}

	private static void registerSkill(int maxLevel) {
		SkillDefinition.SkillDefinitionFile file = SkillDefinition.SkillDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "S", "category": "gathering", "max_level": %d,
						 "level_curve": "lifepath:test_curve"}
						""".formatted(maxLevel)))
				.result().orElseThrow();
		LifepathContent.skills().register(SKILL, SkillDefinition.fromFile(SKILL, file));
	}

	private static void registerCurve(int maxIndex) {
		List<Double> t = new ArrayList<>();
		t.add(0.0);
		for (int i = 1; i <= maxIndex; i++) {
			t.add(t.get(i - 1) + 10.0 * i); // cumulative: 10, 30, 60, 100, 150, ...
		}
		LifepathContent.levelCurves().register(CURVE,
				new LevelCurveDefinition(CURVE, t));
	}

	@Test
	void awardBelowThresholdGrantsXpNoLevel() {
		registerSkill(100);
		registerCurve(100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		var r = SkillXpService.awardXpCore(data, SKILL, 5.0, SRC);
		assertTrue(r.applied());
		assertEquals(0, r.levelsGained());
		assertEquals(5.0, data.skill(SKILL).xp());
		assertEquals(0, data.skill(SKILL).level());
		assertTrue(data.skill(SKILL).lastMeaningfulUse() > 0);
	}

	@Test
	void awardCrossingThresholdLevelsUp() {
		registerSkill(100);
		registerCurve(100); // level1=10xp, level2=30xp
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		var r = SkillXpService.awardXpCore(data, SKILL, 15.0, SRC);
		assertEquals(1, r.newLevel());
		assertEquals(1, r.levelsGained());
		assertEquals(1, data.skill(SKILL).highestLevel());
	}

	@Test
	void awardIsCappedAtMaxLevel() {
		registerSkill(2); // def max 2 despite longer curve
		registerCurve(100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		SkillXpService.awardXpCore(data, SKILL, 100000.0, SRC);
		assertEquals(2, data.skill(SKILL).level());
		assertEquals(2, data.skill(SKILL).highestLevel());
		// At cap: further awards are rejected (applied=false).
		var r = SkillXpService.awardXpCore(data, SKILL, 10.0, SRC);
		assertFalse(r.applied());
	}

	@Test
	void rejectsInvalidInputs() {
		registerSkill(100);
		registerCurve(100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertFalse(SkillXpService.awardXpCore(data, SKILL, 0, SRC).applied());
		assertFalse(SkillXpService.awardXpCore(data, SKILL, -5, SRC).applied());
		assertFalse(SkillXpService.awardXpCore(data, SKILL, Double.NaN, SRC).applied());
		assertFalse(SkillXpService.awardXpCore(data, Identifier.of("lifepath", "nope"), 10, SRC).applied());
		assertEquals(0, data.skills().size()); // rejected awards create no progress
	}

	@Test
	void modifiersApplyInRegistrationOrder() {
		registerSkill(100);
		registerCurve(100);
		List<String> order = new ArrayList<>();
		SkillXpService.registerModifier(Identifier.of("test", "first"), (ctx, amt) -> {
			order.add("first");
			return amt * 2;
		});
		SkillXpService.registerModifier(Identifier.of("test", "second"), (ctx, amt) -> {
			order.add("second");
			return amt + 5;
		});
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		SkillXpService.awardXpCore(data, SKILL, 10.0, SRC);
		assertEquals(List.of("first", "second"), order);
		assertEquals(25.0, data.skill(SKILL).xp()); // (10*2)+5, not (10+5)*2
	}

	@Test
	void modifierCanSuppressAwardButStillCountsAsUse() {
		registerSkill(100);
		registerCurve(100);
		SkillXpService.registerModifier(Identifier.of("test", "zero"), (ctx, amt) -> 0);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		var r = SkillXpService.awardXpCore(data, SKILL, 50.0, SRC);
		assertTrue(r.applied()); // validation passed; practice still happened
		assertEquals(0.0, data.skill(SKILL).xp());
		assertTrue(data.skill(SKILL).lastMeaningfulUse() > 0);
	}

	@Test
	void globalMultiplierReadsConfig() {
		SkillXpService.init(); // registers lifepath:global_multiplier
		registerSkill(100);
		registerCurve(100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		// Config not loaded in tests -> multiplier degrades to identity (1.0).
		SkillXpService.awardXpCore(data, SKILL, 10.0, SRC);
		assertEquals(10.0, data.skill(SKILL).xp());
	}

	@Test
	void setXpCoreRecomputesLevel() {
		registerSkill(100);
		registerCurve(100); // level3 = 60xp cumulative
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		var r = SkillXpService.setXpCore(data, SKILL, 60.0);
		assertTrue(r.applied());
		assertEquals(3, r.newLevel());
		assertEquals(3, SkillXpService.getLevel(data, SKILL));
		assertEquals(60.0, SkillXpService.getProgress(data, SKILL).xp());
	}

	@Test
	void setXpCoreRejectsUnknownAndNegative() {
		registerSkill(100);
		registerCurve(100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertFalse(SkillXpService.setXpCore(data, Identifier.of("lifepath", "nope"), 10).applied());
		assertFalse(SkillXpService.setXpCore(data, SKILL, -1).applied());
	}
}
