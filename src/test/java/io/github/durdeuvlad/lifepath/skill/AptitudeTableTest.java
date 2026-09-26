package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.config.ConfigSpec;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AptitudeTableTest {
	private static final Identifier SKILLS = LifepathMod.id("skills");
	private static final Identifier MINING = Identifier.of("lifepath", "mining");

	@AfterEach
	void tearDown() {
		LifepathConfig.resetForTests();
		LifepathContent.species().clear();
	}

	private static void loadConfig(String toml) throws Exception {
		Path dir = Files.createTempDirectory("apt_cfg");
		Files.writeString(dir.resolve("skills.toml"), toml);
		LifepathConfig.define(SKILLS, ConfigSpec.builder()
				.define("aptitude_s_xp_multiplier", 1.50, "test")
				.define("aptitude_d_decay_multiplier", 1.25, "test")
				.build());
		LifepathConfig.loadAll(dir);
	}

	@Test
	void specDefaultsWhenNoConfigLoaded() {
		// No config at all → documented spec defaults (§8).
		assertEquals(0.70, AptitudeTable.xpMultiplier(Aptitude.D), 1e-9);
		assertEquals(1.00, AptitudeTable.xpMultiplier(Aptitude.B), 1e-9);
		assertEquals(1.50, AptitudeTable.xpMultiplier(Aptitude.S), 1e-9);
		assertEquals(1.25, AptitudeTable.decayMultiplier(Aptitude.D), 1e-9);
		assertEquals(0.60, AptitudeTable.decayMultiplier(Aptitude.S), 1e-9);
	}

	@Test
	void configOverrideChangesMultiplier() throws Exception {
		loadConfig("aptitude_s_xp_multiplier = 2.0\naptitude_d_decay_multiplier = 0.5\n");
		assertEquals(2.0, AptitudeTable.xpMultiplier(Aptitude.S), 1e-9);
		assertEquals(0.5, AptitudeTable.decayMultiplier(Aptitude.D), 1e-9);
		// Unoverridden grades keep spec defaults.
		assertEquals(1.00, AptitudeTable.xpMultiplier(Aptitude.B), 1e-9);
	}

	@Test
	void speciesFloorRaisesEffectiveGrade() {
		LifepathContent.species().register(Identifier.of("lifepath", "dwarf"),
				new SpeciesDefinition(Identifier.of("lifepath", "dwarf"), "Dwarf",
						SpeciesDefinition.Visibility.NORMAL, SpeciesDefinition.Selection.OPEN,
						List.of(), List.of(), Map.of(MINING, Aptitude.B),
						List.of(), Optional.empty(), Optional.empty()));

		SkillProgress dGrade = new SkillProgress(0, 0, 0, 0, Aptitude.D, 0L);
		SkillProgress aGrade = new SkillProgress(0, 0, 0, 0, Aptitude.A, 0L);

		// Dwarf mining floor B: D→B raised, A stays (max rule, never lowers).
		assertEquals(Aptitude.B, SkillService.effectiveAptitude(
				Identifier.of("lifepath", "dwarf"), MINING, dGrade));
		assertEquals(Aptitude.A, SkillService.effectiveAptitude(
				Identifier.of("lifepath", "dwarf"), MINING, aGrade));
		// No floor for other skills.
		assertEquals(Aptitude.D, SkillService.effectiveAptitude(
				Identifier.of("lifepath", "dwarf"), Identifier.of("lifepath", "fishing"), dGrade));
		// No species → recorded grade.
		assertEquals(Aptitude.C, SkillService.effectiveAptitude(
				null, MINING, new SkillProgress(0, 0, 0, 0, Aptitude.C, 0L)));
		// Unknown species → no floor (graceful).
		assertEquals(Aptitude.D, SkillService.effectiveAptitude(
				Identifier.of("lifepath", "nonexistent"), MINING, dGrade));
	}
}
