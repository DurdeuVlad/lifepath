package com.dwurdy.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.config.ConfigSpec;
import com.dwurdy.lifepath.config.LifepathConfig;
import com.dwurdy.lifepath.skill.RankBands.RankBand;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RankBandsTest {
	private static final ResourceLocation SKILLS = LifepathMod.id("skills");

	private static void defineBands(int... values) throws Exception {
		ConfigSpec.Builder b = ConfigSpec.builder();
		String[] keys = {"band_untrained", "band_novice", "band_apprentice", "band_skilled",
				"band_expert", "band_master", "band_legendary"};
		for (int i = 0; i < keys.length; i++) {
			b.define(keys[i], values[i], "test band");
		}
		LifepathConfig.define(SKILLS, b.build());
		// Load into a throwaway dir: missing file -> spec defaults become the live values.
		LifepathConfig.loadAll(java.nio.file.Files.createTempDirectory("bands"));
	}

	@Test
	void defaultBoundaries() {
		LifepathConfig.resetForTests();
		int[] expectedLevels = {0, 1, 19, 20, 39, 40, 59, 60, 79, 80, 94, 95, 100};
		RankBand[] expected = {
				RankBand.UNTRAINED,
				RankBand.NOVICE, RankBand.NOVICE,
				RankBand.APPRENTICE, RankBand.APPRENTICE,
				RankBand.SKILLED, RankBand.SKILLED,
				RankBand.EXPERT, RankBand.EXPERT,
				RankBand.MASTER, RankBand.MASTER,
				RankBand.LEGENDARY, RankBand.LEGENDARY};
		for (int i = 0; i < expectedLevels.length; i++) {
			assertEquals(expected[i], RankBands.bandFor(expectedLevels[i]),
					"level " + expectedLevels[i]);
		}
	}

	@Test
	void negativeLevelIsUntrained() {
		LifepathConfig.resetForTests();
		assertEquals(RankBand.UNTRAINED, RankBands.bandFor(-5));
	}

	@Test
	void configOverridesBoundaries() throws Exception {
		LifepathConfig.resetForTests();
		// Tighten: legendary at 50.
		defineBands(0, 1, 10, 20, 30, 40, 50);
		assertEquals(RankBand.LEGENDARY, RankBands.bandFor(50));
		assertEquals(RankBand.MASTER, RankBands.bandFor(49));
		assertEquals(RankBand.APPRENTICE, RankBands.bandFor(19));
	}

	@Test
	void invalidThresholdsFallBackToDefaults() throws Exception {
		LifepathConfig.resetForTests();
		// Non-ascending: legendary(50) < master(80). Under the BROKEN config,
		// bandFor(75) would be LEGENDARY (75>=50); under defaults it's EXPERT.
		defineBands(0, 1, 20, 40, 60, 80, 50);
		assertEquals(RankBand.EXPERT, RankBands.bandFor(75),
				"non-ascending config must fall back to defaults");
		org.junit.jupiter.api.Assertions.assertArrayEquals(
				new int[] {0, 1, 20, 40, 60, 80, 95}, RankBands.thresholds());
	}

	@Test
	void zeroUntrainedThresholdRequired() throws Exception {
		LifepathConfig.resetForTests();
		// untrained=5 is invalid. Under the broken config level 5 hits the
		// untrained band itself; under defaults level 5 is NOVICE — the assertion
		// discriminates applied-invalid from fallback.
		defineBands(5, 10, 20, 40, 60, 80, 95);
		assertEquals(RankBand.NOVICE, RankBands.bandFor(5),
				"untrained!=0 config must fall back to defaults");
	}

	@Test
	void bandKeyIsStableLowercase() {
		assertEquals("legendary", RankBand.LEGENDARY.key());
		assertEquals("untrained", RankBand.UNTRAINED.key());
	}
}
