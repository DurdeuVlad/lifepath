package com.dwurdy.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload;
import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M6-2: {@link SkillSummary} turns registry skills + player progress into
 * display cards — numbers, rank key, decay inputs, milestone text, hints.
 */
class SkillSummaryTest {
	private static final Path DATA = Path.of("src/main/resources/data/lifepath");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		LifepathContent.skills().clear();
		// RankBands reads shared config state — sibling tests pollute it.
		com.dwurdy.lifepath.config.LifepathConfig.resetForTests();
	}

	@AfterEach
	void tearDown() {
		LifepathContent.skills().clear();
		com.dwurdy.lifepath.config.LifepathConfig.resetForTests();
	}

	private static SkillDefinition skill(String name) throws Exception {
		return skill(name, null);
	}

	private static SkillDefinition skill(String name, String icon)
			throws Exception {
		var json = JsonParser.parseString(Files.readString(
				DATA.resolve("skill/" + name + ".json"))).getAsJsonObject();
		if (icon != null) {
			json.addProperty("icon", icon);
		}
		var file = SkillDefinition.SkillDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, json)
				.result().orElseThrow();
		return SkillDefinition.fromFile(LifepathMod.id(name), file);
	}

	@Test
	void everyRegisteredSkillGetsACardEvenWithoutProgress() throws Exception {
		LifepathContent.skills().register(LifepathMod.id("mining"), skill("mining"));
		SkillsSummaryPayload p = SkillSummary.build(data, System.currentTimeMillis());
		assertEquals(1, p.skills().size());
		SkillCard c = p.skills().get(0);
		assertEquals("Mining", c.display().name().getString());
		assertEquals("untrained", c.display().rankKey());
		assertEquals(0, c.progress().level());
		assertEquals(SkillSummary.NEVER_PRACTICED, c.progress().graceEndsEpochMs());
		assertTrue(c.display().improveHint().getString().contains("Mine"),
				"shipped hint reaches the card");
		// First milestone is next (level 0 < 10).
		assertEquals(10, c.details().nextMilestoneLevel());
		assertEquals("lifepath.skill.mining.milestone.10",
				c.details().nextMilestoneText());
	}

	@Test
	void iconRefFlowsIntoDisplay() throws Exception {
		// M12-2: a declared icon reaches the card as a normalized texture id;
		// a def without one yields "" (client resolves the placeholder).
		LifepathContent.skills().register(LifepathMod.id("mining"),
				skill("mining", "skill/mining"));
		SkillsSummaryPayload p = SkillSummary.build(data, System.currentTimeMillis());
		assertEquals("lifepath:textures/gui/skill/mining.png",
				p.skills().get(0).display().icon());
	}

	@Test
	void progressPopulatesLevelBandAndBar() throws Exception {
		LifepathContent.skills().register(LifepathMod.id("mining"), skill("mining"));
		// Unit tests resolve xpIn against the LevelCurves fallback (level-1
		// threshold 40) — 50 XP → level 1 with 10 XP in.
		data.setSkillProgress(LifepathMod.id("mining"),
				new SkillProgress(50.0, 1, 1, 0, Aptitude.A, 0L));
		SkillsSummaryPayload p = SkillSummary.build(data, System.currentTimeMillis());
		SkillCard c = p.skills().get(0);
		assertEquals(1, c.progress().level());
		assertEquals(10.0, c.progress().xpIn(), 0.001);
		assertTrue(c.progress().xpNeed() > 0);
		assertEquals("A", c.display().aptitude());
		assertTrue(c.details().bonuses().isEmpty(),
				"no milestone unlocked below level 10");
		assertEquals(10, c.details().nextMilestoneLevel());
	}

	@Test
	void milestonesBecomeBonusesWhenPassed() throws Exception {
		LifepathContent.skills().register(LifepathMod.id("mining"), skill("mining"));
		data.setSkillProgress(LifepathMod.id("mining"),
				new SkillProgress(0.0, 60, 60, 30, Aptitude.B, 0L));
		SkillsSummaryPayload p = SkillSummary.build(data, System.currentTimeMillis());
		SkillCard c = p.skills().get(0);
		// Level 60 → next milestone is 70; floor 30 reaches the card.
		assertEquals(70, c.details().nextMilestoneLevel());
		assertEquals(30, c.progress().protectedFloor());
		assertEquals("expert", c.display().rankKey(),
				"bandFor(60) is the expert band (thresholds 0/1/20/40/60/80/95)");
	}

	@Test
	void decayInputsReflectPracticeAndFloor() throws Exception {
		SkillDefinition def = skill("mining");
		LifepathContent.skills().register(LifepathMod.id("mining"), def);
		long now = System.currentTimeMillis();
		// Practiced recently → grace end in the future.
		data.setSkillProgress(LifepathMod.id("mining"),
				new SkillProgress(50.0, 1, 1, 0, Aptitude.B, now));
		SkillCard c = SkillSummary.build(data, now).skills().get(0);
		assertTrue(c.progress().graceEndsEpochMs() > now,
				"grace end should be last-use + grace window");
	}
}
