package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.Optional;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillServiceTest {
	private static final Identifier MINING = Identifier.of("lifepath", "mining");
	private static final Identifier UNKNOWN = Identifier.of("lifepath", "does_not_exist");

	private static void registerSkill(Identifier id, int maxLevel) {
		SkillDefinition.SkillDefinitionFile file = SkillDefinition.SkillDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "S", "category": "gathering", "max_level": %d}
						""".formatted(maxLevel)))
				.result().orElseThrow();
		LifepathContent.skills().register(id, SkillDefinition.fromFile(id, file));
	}

	@BeforeEach
	void setUp() {
		LifepathContent.skills().clear();
		SkillService.resetForTests();
	}

	@Test
	void unknownIdIsEmptyNotCrash() {
		assertTrue(SkillService.definition(UNKNOWN).isEmpty());
		assertEquals(100, SkillService.maxLevel(UNKNOWN));
	}

	@Test
	void definitionFoundAfterRegister() {
		registerSkill(MINING, 50);
		assertTrue(SkillService.definition(MINING).isPresent());
		assertEquals(50, SkillService.maxLevel(MINING));
	}

	@Test
	void clampedEnforcesLevelBounds() {
		SkillProgress p = new SkillProgress(0, 150, 150, 10, Aptitude.C, 0);
		SkillProgress c = SkillService.clamped(p, 100);
		assertEquals(100, c.level());
		// highestLevel is a historical peak: it never decreases, even when the
		// definition's maxLevel was lowered below it.
		assertEquals(150, c.highestLevel());
		assertEquals(10, c.protectedFloor());
	}

	@Test
	void clampedNeverLowersHighest() {
		SkillProgress p = new SkillProgress(0, 30, 80, 5, Aptitude.C, 0);
		SkillProgress c = SkillService.clamped(p, 100);
		assertEquals(30, c.level());
		assertEquals(80, c.highestLevel());
	}

	@Test
	void clampedRepairsHighestBelowLevel() {
		SkillProgress p = new SkillProgress(0, 50, 20, 5, Aptitude.C, 0);
		SkillProgress c = SkillService.clamped(p, 100);
		assertEquals(50, c.highestLevel());
	}

	@Test
	void floorNeverExceedsLevel() {
		SkillProgress p = new SkillProgress(0, 10, 60, 55, Aptitude.C, 0);
		SkillProgress c = SkillService.clamped(p, 100);
		assertEquals(10, c.protectedFloor());
	}

	@Test
	void clampedFixesNegativeXpAndUse() {
		SkillProgress p = new SkillProgress(-5.5, 0, 0, -3, Aptitude.C, -99);
		SkillProgress c = SkillService.clamped(p, 100);
		assertEquals(0.0, c.xp());
		assertEquals(0, c.protectedFloor());
		assertEquals(0, c.lastMeaningfulUse());
	}

	@Test
	void clampedFixesNonFiniteXp() {
		// Codec.DOUBLE will decode crafted/corrupt NaN/Inf — clamp must zero them.
		assertEquals(0.0, SkillService.clamped(
				new SkillProgress(Double.NaN, 0, 0, 0, Aptitude.C, 0), 100).xp());
		assertEquals(0.0, SkillService.clamped(
				new SkillProgress(Double.POSITIVE_INFINITY, 0, 0, 0, Aptitude.C, 0), 100).xp());
	}

	@Test
	void withLevelCannotFabricateHighest() {
		// newLevel above maxLevel must not record a peak that never existed.
		SkillProgress up = SkillService.withLevel(SkillProgress.fresh(Aptitude.C), 150, 100);
		assertEquals(100, up.level());
		assertEquals(100, up.highestLevel());
	}

	@Test
	void progressIsReadOnly() {
		registerSkill(MINING, 100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertNull(SkillService.progress(data, MINING)); // absent -> null, no creation
		assertNull(data.skill(MINING));
		data.setSkillProgress(MINING, new SkillProgress(0, 500, 500, 0, Aptitude.A, 0));
		SkillProgress p = SkillService.progress(data, MINING);
		assertEquals(100, p.level()); // clamped view
		assertEquals(500, data.skill(MINING).level()); // model NOT rewritten on read
	}

	@Test
	void withLevelUpdatesHighestAndPreservesFloor() {
		SkillProgress p = new SkillProgress(12.5, 10, 10, 4, Aptitude.B, 123);
		SkillProgress up = SkillService.withLevel(p, 25, 100);
		assertEquals(25, up.level());
		assertEquals(25, up.highestLevel());
		assertEquals(4, up.protectedFloor());
		assertEquals(12.5, up.xp());
		assertEquals(Aptitude.B, up.aptitude());

		SkillProgress down = SkillService.withLevel(up, 3, 100);
		assertEquals(3, down.level());
		assertEquals(25, down.highestLevel());
		assertEquals(3, down.protectedFloor()); // floor clamped to new level
	}

	@Test
	void ensureProgressCreatesAndStoresFresh() {
		registerSkill(MINING, 100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		SkillProgress p = SkillService.ensureProgress(data, MINING);
		assertEquals(0, p.level());
		assertEquals(Aptitude.C, p.aptitude());
		assertSame(p, data.skill(MINING));
	}

	@Test
	void ensureProgressReturnsNullForUnknownSkill() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertNull(SkillService.ensureProgress(data, UNKNOWN));
		assertNull(data.skill(UNKNOWN));
	}

	@Test
	void ensureProgressRepairsBrokenExisting() {
		registerSkill(MINING, 100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(MINING, new SkillProgress(0, 400, 400, 0, Aptitude.A, 0));
		SkillProgress p = SkillService.ensureProgress(data, MINING);
		assertEquals(100, p.level());
	}

	@Test
	void registeredSkillRoundTripsThroughSkillMap() {
		registerSkill(MINING, 100);
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSkillProgress(MINING, SkillService.withLevel(SkillProgress.fresh(Aptitude.S), 42, 100));
		assertEquals(42, data.skill(MINING).level());
		assertEquals(Optional.of(42), Optional.ofNullable(data.skill(MINING)).map(SkillProgress::level));
	}
}
