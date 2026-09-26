package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.config.ConfigSpec;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.skill.RankBands.RankBand;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class RankBandsTest {
	private static final Identifier SKILLS = LifepathMod.id("skills");

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
		// Non-ascending: legendary(50) < master(80).
		defineBands(0, 1, 20, 40, 60, 80, 50);
		assertEquals(RankBand.LEGENDARY, RankBands.bandFor(95));
		assertEquals(RankBand.MASTER, RankBands.bandFor(80));
	}

	@Test
	void zeroUntrainedThresholdRequired() throws Exception {
		LifepathConfig.resetForTests();
		// Untrained floor moved off 0 — invalid.
		defineBands(5, 10, 20, 40, 60, 80, 95);
		assertEquals(RankBand.UNTRAINED, RankBands.bandFor(0));
	}

	@Test
	void bandKeyIsStableLowercase() {
		assertEquals("legendary", RankBand.LEGENDARY.key());
		assertEquals("untrained", RankBand.UNTRAINED.key());
	}
}
