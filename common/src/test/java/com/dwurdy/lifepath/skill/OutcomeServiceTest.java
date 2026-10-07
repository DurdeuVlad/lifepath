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
		// ItemStack/component paths need the vanilla registries up.
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
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

	private static OutcomeRuleDefinition.BandModifiers mods(double count, double fail,
			double failCount, Optional<String> quality, boolean sign) {
		return new OutcomeRuleDefinition.BandModifiers(count, fail, failCount,
				quality, sign, 1.0, 0.0);
	}

	private static OutcomeRuleDefinition rule(Map<RankBands.RankBand, OutcomeRuleDefinition.BandModifiers> bands) {
		return new OutcomeRuleDefinition(LifepathMod.id("test_rule"), SMITHING,
				Optional.of(SMITHING_ACTIVITY), Set.of(), Set.of(), Set.of(), bands);
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
				RankBands.RankBand.UNTRAINED, mods(0.8, 0.15, 0.5,
						Optional.of("crude"), false),
				RankBands.RankBand.MASTER, mods(1.25, 0.0, 0.5,
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
				Optional.of(SMITHING_ACTIVITY), Set.of(),
				Set.of(LifepathMod.id("smithing_tier_diamond")),
				Set.of(), Map.of(RankBands.RankBand.NOVICE,
						mods(0.9, 0.0, 0.5, Optional.empty(), false))));
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
				Optional.empty(), Set.of(), Set.of(),
				Set.of(rl("minecraft", "shears")),
				Map.of(RankBands.RankBand.UNTRAINED, mods(0.5, 0.0, 0.5,
						Optional.empty(), false))));
		try {
			assertEquals(OutcomeService.Outcome.IDENTITY, OutcomeService.resolve(data,
					SMITHING_ACTIVITY, rl("minecraft", "shears"), Set.of()));
		} finally {
			LifepathContent.outcomeRules().clear();
		}
	}

	@Test
	void subjectListRestrictsMatching() {
		register(new OutcomeRuleDefinition(LifepathMod.id("subject_rule"), SMITHING,
				Optional.empty(), Set.of(rl("create", "shaft")), Set.of(), Set.of(),
				Map.of(RankBands.RankBand.NOVICE, mods(0.9, 0.0, 0.5,
						Optional.empty(), false))));
		try {
			SkillXpService.setLevelCore(data, SMITHING, 5);
			// Subject-listed item matches.
			assertEquals(0.9, OutcomeService.resolve(data, SMITHING_ACTIVITY,
					rl("create", "shaft"), Set.of()).countMult());
			// Anything else doesn't.
			assertEquals(OutcomeService.Outcome.IDENTITY, OutcomeService.resolve(data,
					SMITHING_ACTIVITY, rl("create", "cogwheel"), Set.of()));
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
				SMITHING, Optional.empty(), List.of(), List.of(), List.of(),
				Map.of("grandmaster", OutcomeRuleDefinition.BandModifiers.IDENTITY,
						"untrained", mods(0.9, 0.0, 0.5, Optional.empty(), false)));
		OutcomeRuleDefinition def = OutcomeRuleDefinition.fromFile(LifepathMod.id("warn"), file);
		assertEquals(1, def.bands().size());
		assertEquals(0.9, def.forBand(RankBands.RankBand.UNTRAINED).outputCountMult());
		assertNull(def.bands().get(RankBands.RankBand.MASTER));
	}

	@Test
	void applyDropScalesStackableCountsProbabilistically() {
		// 0.6 on a 1-count stack: ~60% survive as 1, rest drop to 0.
		OutcomeService.Outcome low = new OutcomeService.Outcome(
				0.6, 0.0, 0.5, null, false, 1.0, 0.0);
		net.minecraft.util.RandomSource seeded = net.minecraft.util.RandomSource.create(42);
		int kept = 0;
		for (int i = 0; i < 1000; i++) {
			var s = new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.STONE, 1);
			if (OutcomeService.applyDrop("Tester", seeded, low, s)) {
				kept++;
				assertEquals(1, s.getCount());
			}
		}
		assertTrue(kept > 520 && kept < 680, "0.6 roll kept " + kept + "/1000");

		// 1.5 on a 1-count stack: always >=1, ~50% get a bonus copy.
		OutcomeService.Outcome high = new OutcomeService.Outcome(
				1.5, 0.0, 0.5, null, false, 1.0, 0.0);
		seeded = net.minecraft.util.RandomSource.create(42);
		int total = 0;
		for (int i = 0; i < 1000; i++) {
			var s = new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.STONE, 1);
			assertTrue(OutcomeService.applyDrop("Tester", seeded, high, s));
			assertTrue(s.getCount() >= 1 && s.getCount() <= 2);
			total += s.getCount();
		}
		assertTrue(total > 1400 && total < 1600, "1.5 roll totalled " + total);
	}

	@Test
	void applyDropTreatsUnstackableAsDropChance() {
		OutcomeService.Outcome zero = new OutcomeService.Outcome(
				0.0, 0.0, 0.5, null, false, 1.0, 0.0);
		net.minecraft.util.RandomSource seeded = net.minecraft.util.RandomSource.create(7);
		// unstackable (maxStackSize 1): countMult < 1 can void the drop entirely.
		var sword = new net.minecraft.world.item.ItemStack(
				net.minecraft.world.item.Items.IRON_SWORD, 1);
		for (int i = 0; i < 10; i++) {
			sword = new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.IRON_SWORD, 1);
			assertTrue(!OutcomeService.applyDrop("Tester", seeded, zero, sword)
					|| !sword.isEmpty());
		}
	}

	private static ResourceLocation rl(String ns, String path) {
		return ResourceLocation.fromNamespaceAndPath(ns, path);
	}
}
