package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.LevelCurveDefinition;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Diminishing-returns tests (M3-4): tier boundaries, window expiry,
 * relog persistence, and the award-path integration.
 */
class DiminishingReturnsTest {
	private static final ResourceLocation SKILL = ResourceLocation.fromNamespaceAndPath("lifepath", "mining");
	private static final ResourceLocation CURVE = ResourceLocation.fromNamespaceAndPath("lifepath", "dim_curve");
	private static final ActivityEvent STONE = ActivityEvent.of(
			ResourceLocation.fromNamespaceAndPath("lifepath", "mining"), ResourceLocation.fromNamespaceAndPath("minecraft", "stone"));
	private static final ActivityEvent DIRT = ActivityEvent.of(
			ResourceLocation.fromNamespaceAndPath("lifepath", "mining"), ResourceLocation.fromNamespaceAndPath("minecraft", "dirt"));
	private static final long HOUR = 3_600_000L;
	private static final long T0 = 1_700_000_000_000L;
	private static final long WINDOW = 4 * HOUR;

	@BeforeEach
	void setUp() {
		LifepathContent.skills().clear();
		LifepathContent.levelCurves().clear();
		SkillXpService.resetForTests();
		SkillService.resetForTests();
		List<Double> t = new ArrayList<>();
		for (int i = 0; i <= 101; i++) {
			t.add(10.0 * i);
		}
		LifepathContent.levelCurves().register(CURVE, new LevelCurveDefinition(CURVE, t));
		LifepathContent.skills().register(SKILL, new SkillDefinition(SKILL, "M",
				SkillDefinition.Category.GATHERING, 100, Optional.of(CURVE), List.of(),
				List.of(), Optional.empty()));
	}

	@Test
	void tierBoundaries() {
		assertEquals(1.0, DiminishingReturns.multiplierFor(64));
		assertEquals(0.5, DiminishingReturns.multiplierFor(65));
		assertEquals(0.5, DiminishingReturns.multiplierFor(256));
		assertEquals(0.1, DiminishingReturns.multiplierFor(257));
		assertEquals(0.1, DiminishingReturns.multiplierFor(10_000));
	}

	@Test
	void recordAndCountPrunesExpiredEntries() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		String sig = STONE.repetitionSignature();
		// Two ancient entries + two live ones.
		data.setActionTimestamps(sig, List.of(1L, 2L, T0 - 1000, T0 - 500));
		int n = DiminishingReturns.recordAndCount(data, sig, T0, WINDOW);
		assertEquals(3, n, "two expired entries pruned, two live + this record");
		assertEquals(List.of(T0 - 1000, T0 - 500, T0), data.actionSignatures().get(sig));
	}

	@Test
	void differentSignaturesDoNotShareThePenalty() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		String stone = STONE.repetitionSignature();
		String dirt = DIRT.repetitionSignature();
		for (int i = 0; i < 70; i++) {
			DiminishingReturns.recordAndCount(data, stone, T0 + i, WINDOW);
		}
		// 70 stones → tier 2; a first dirt is untouched.
		assertEquals(0.5, DiminishingReturns.multiplierFor(
				DiminishingReturns.count(data, stone, T0 + 100, WINDOW)));
		assertEquals(1.0, DiminishingReturns.multiplierFor(
				DiminishingReturns.recordAndCount(data, dirt, T0 + 100, WINDOW)));
	}

	@Test
	void signatureWindowSurvivesSerializeRoundTrip() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		String sig = STONE.repetitionSignature();
		for (int i = 0; i < 100; i++) {
			DiminishingReturns.recordAndCount(data, sig, T0 + i, WINDOW);
		}
		PlayerCharacterData restored = io.github.durdeuvlad.lifepath.character.persistence
				.CharacterPersistence.deserialize(
						io.github.durdeuvlad.lifepath.character.persistence
								.CharacterPersistence.serialize(data));
		assertEquals(100, DiminishingReturns.count(restored, sig, T0 + 200, WINDOW),
				"a relog must not reset the spam penalty");
	}

	@Test
	void awardPathAppliesThePenaltyAndRecords() {
		SkillXpService.init();
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		// Spam 300 identical stone awards: late awards must yield far less xp.
		double early = 0, late = 0;
		for (int i = 0; i < 300; i++) {
			double before = data.skill(SKILL) == null ? 0 : data.skill(SKILL).xp();
			// Small awards keep the level below cap so the discount is visible.
			SkillXpService.awardXpCore(data, SKILL, 0.5, STONE);
			double gained = data.skill(SKILL).xp() - before;
			if (i == 0) {
				early = gained;
			}
			if (i == 299) {
				late = gained;
			}
		}
		assertTrue(early > 0 && late < early * 0.2,
				"spam must decay the award: early=" + early + " late=" + late);
		// A different source id gets full xp — variety is not penalized.
		double before = data.skill(SKILL).xp();
		SkillXpService.awardXpCore(data, SKILL, 0.5, DIRT);
		assertEquals(0.5, data.skill(SKILL).xp() - before, 0.001);
	}

	@Test
	void oneEventRoutedTwiceCountsOnce() {
		SkillXpService.init();
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		// A single activity event fanned out to two awards must record ONE
		// signature entry — the ledger counts actions, not awards.
		ActivityEvent event = new ActivityEvent(null, ResourceLocation.fromNamespaceAndPath("lifepath", "mining"),
				ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), java.util.Set.of(),
				ActivityEvent.Cause.PLAYER, T0, java.util.Map.of());
		SkillXpService.awardXpCore(data, SKILL, 0.5, event);
		SkillXpService.awardXpCore(data, SKILL, 0.5, event);
		assertEquals(1, DiminishingReturns.count(data, event.repetitionSignature(),
				T0 + 1000, WINDOW));
	}

	@Test
	void pruneSweepDropsDeadSignatures() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setActionTimestamps("a|b", List.of(1L, 2L));          // all expired
		data.setActionTimestamps("c|d", List.of(1L, T0 - 50));     // mixed
		data.pruneActionSignatures(T0 - WINDOW);
		assertTrue(!data.actionSignatures().containsKey("a|b"),
				"dead signature key must be dropped");
		assertEquals(List.of(T0 - 50), data.actionSignatures().get("c|d"));
	}
}
