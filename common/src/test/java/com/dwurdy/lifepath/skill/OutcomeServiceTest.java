package com.dwurdy.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.OutcomeRuleDefinition;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M22 OutcomeService contract: rules map a skill's rank band to output
 * modifiers; no matching rule or the config gate returns identity; magnitude
 * caps clamp datapack values. Resolution is exercised through {@link
 * PlayerCharacterData} — the same path {@code apply} uses with a live player.
 */
class OutcomeServiceTest {

	private static final ResourceLocation SMITHING = LifepathMod.id("smithing");
	private static final ResourceLocation SMITHING_ACTIVITY = LifepathMod.id("smithing");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		LifepathContent.skills().clear();
		LifepathContent.levelCurves().clear();
		// A real skill def + curve: setLevelCore resolves through them.
		SkillDefinition.SkillDefinitionFile file = SkillDefinition.SkillDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(
						"{\"display_name\":\"S\",\"category\":\"gathering\",\"max_level\":100}"))
				.result().orElseThrow();
		LifepathContent.skills().register(SMITHING, SkillDefinition.fromFile(SMITHING, file));
		// 101-entry curve so setLevelCore can reach any level (absent curve
		// degrades to the 6-level fallback).
		java.util.List<Double> t = new java.util.ArrayList<>();
		t.add(0.0);
		for (int i = 1; i <= 100; i++) {
			t.add(t.get(i - 1) + 10.0 * i);
		}
		LifepathContent.levelCurves().register(LevelCurves.DEFAULT_ID,
				new com.dwurdy.lifepath.content.LevelCurveDefinition(
						LevelCurves.DEFAULT_ID, t));
	}

	@org.junit.jupiter.api.AfterEach
	void tearDown() {
		LifepathContent.outcomeRules().clear();
		LifepathContent.skills().clear();
		LifepathContent.levelCurves().clear();
	}

	private static OutcomeRuleDefinition rule(Map<RankBands.RankBand, OutcomeRuleDefinition.BandModifiers> bands) {
		return new OutcomeRuleDefinition(LifepathMod.id("test_rule"), SMITHING,
				Optional.of(SMITHING_ACTIVITY), Set.of(), Set.of(), bands);
	}

	private static void register(OutcomeRuleDefinition def) {
		LifepathContent.outcomeRules().register(def.id(), def);
	}

	@Test
	void resolveReturnsIdentityWithoutMatchingRule() {
		assertEquals(OutcomeService.Outcome.IDENTITY,
				OutcomeService.resolve(data, SMITHING_ACTIVITY,
						rl("minecraft", "iron_sword"), Set.of()));
	}

	@Test
	void resolveMapsLevelToBandModifiers() {
		register(rule(Map.of(
				RankBands.RankBand.UNTRAINED, new OutcomeRuleDefinition.BandModifiers(0.8, 0.15, 0.5,
						Optional.of("crude"), false),
				RankBands.RankBand.MASTER, new OutcomeRuleDefinition.BandModifiers(1.25, 0.0, 0.5,
						Optional.of("masterwork"), true))));
		try {
			SkillXpService.setLevelCore(data, SMITHING, 0);
			OutcomeService.Outcome low = OutcomeService.resolve(data,
					SMITHING_ACTIVITY, rl("minecraft", "iron_sword"), Set.of());
			assertEquals(0.8, low.countMult());
			assertEquals(0.15, low.failureChance());
			assertEquals("crude", low.qualityTier());

			SkillXpService.setLevelCore(data, SMITHING, 85);
			OutcomeService.Outcome high = OutcomeService.resolve(data,
					SMITHING_ACTIVITY, rl("minecraft", "iron_sword"), Set.of());
			assertEquals(1.25, high.countMult());
			assertEquals(0.0, high.failureChance());
			assertEquals("masterwork", high.qualityTier());
			assertTrue(high.signItems());
		} finally {
			LifepathContent.outcomeRules().clear();
		}
	}

	@Test
	void resolveHonorsActivityAndTagMatching() {
		register(new OutcomeRuleDefinition(LifepathMod.id("tagged_rule"), SMITHING,
				Optional.of(SMITHING_ACTIVITY),
				Set.of(LifepathMod.id("smithing_tier_diamond")),
				Set.of(), Map.of(RankBands.RankBand.NOVICE,
						new OutcomeRuleDefinition.BandModifiers(0.9, 0.0, 0.5,
								Optional.empty(), false))));
		try {
			SkillXpService.setLevelCore(data, SMITHING, 5);
			// Wrong activity → no match
			assertEquals(OutcomeService.Outcome.IDENTITY, OutcomeService.resolve(data,
					com.dwurdy.lifepath.event.ActivityTypes.CRAFTING,
					rl("minecraft", "diamond_sword"),
					Set.of(LifepathMod.id("smithing_tier_diamond"))));
			// Right activity, missing required tag → no match
			assertEquals(OutcomeService.Outcome.IDENTITY, OutcomeService.resolve(data,
					SMITHING_ACTIVITY, rl("minecraft", "iron_sword"), Set.of()));
			// Right activity + tag → resolves
			OutcomeService.Outcome o = OutcomeService.resolve(data, SMITHING_ACTIVITY,
					rl("minecraft", "diamond_sword"),
					Set.of(LifepathMod.id("smithing_tier_diamond")));
			assertEquals(0.9, o.countMult());
		} finally {
			LifepathContent.outcomeRules().clear();
		}
	}

	@Test
	void excludedSubjectsNeverMatch() {
		register(new OutcomeRuleDefinition(LifepathMod.id("excl_rule"), SMITHING,
				Optional.empty(), Set.of(), Set.of(rl("minecraft", "shears")),
				Map.of(RankBands.RankBand.UNTRAINED, new OutcomeRuleDefinition.BandModifiers(0.5, 0.0, 0.5,
						Optional.empty(), false))));
		try {
			assertEquals(OutcomeService.Outcome.IDENTITY, OutcomeService.resolve(data,
					SMITHING_ACTIVITY, rl("minecraft", "shears"), Set.of()));
		} finally {
			LifepathContent.outcomeRules().clear();
		}
	}

	@Test
	void bandCodecParsesAndRejectsBadRanges() {
		var ops = JsonOps.INSTANCE;
		var parsed = OutcomeRuleDefinition.BandModifiers.CODEC.parse(ops,
				JsonParser.parseString("{\"output_count_mult\":1.25,\"failure_chance\":0.1,"
						+ "\"quality_tier\":\"fine\",\"sign_items\":true}"));
		assertTrue(parsed.result().isPresent());
		OutcomeRuleDefinition.BandModifiers m = parsed.result().get();
		assertEquals(1.25, m.outputCountMult());
		assertEquals("fine", m.qualityTier().orElseThrow());
		// failure_chance above 1 must not decode.
		assertTrue(OutcomeRuleDefinition.BandModifiers.CODEC.parse(ops,
				JsonParser.parseString("{\"failure_chance\":1.5}")).result().isEmpty());
	}

	@Test
	void unknownBandKeysAreWarnedNotFatal() {
		OutcomeRuleDefinition.OutcomeRuleFile file = new OutcomeRuleDefinition.OutcomeRuleFile(
				SMITHING, Optional.empty(), List.of(), List.of(),
				Map.of("grandmaster", OutcomeRuleDefinition.BandModifiers.IDENTITY,
						"untrained", new OutcomeRuleDefinition.BandModifiers(0.9, 0.0, 0.5,
								Optional.empty(), false)));
		OutcomeRuleDefinition def = OutcomeRuleDefinition.fromFile(LifepathMod.id("warn"), file);
		assertEquals(1, def.bands().size());
		assertEquals(0.9, def.forBand(RankBands.RankBand.UNTRAINED).outputCountMult());
		assertNull(def.bands().get(RankBands.RankBand.MASTER));
	}

	private static ResourceLocation rl(String ns, String path) {
		return ResourceLocation.fromNamespaceAndPath(ns, path);
	}
}
