package com.dwurdy.lifepath.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.skill.LevelCurves;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LevelCurveTest {
	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("lifepath", "test_curve");

	private static LevelCurveDefinition curve(double... thresholds) {
		List<Double> t = java.util.Arrays.stream(thresholds).boxed().toList();
		return LevelCurveDefinition.fromFile(ID, new LevelCurveDefinition.LevelCurveFile(t));
	}

	@Test
	void parsesAndEvaluates() {
		var file = LevelCurveDefinition.LevelCurveFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(
						"{\"thresholds\": [0, 10, 30, 60]}"))
				.result().orElseThrow();
		LevelCurveDefinition def = LevelCurveDefinition.fromFile(ID, file);
		assertEquals(3, def.maxAttainableLevel());
		assertEquals(0, def.levelFor(0));
		assertEquals(0, def.levelFor(9.9));
		assertEquals(1, def.levelFor(10));
		assertEquals(1, def.levelFor(29.9));
		assertEquals(2, def.levelFor(30));
		assertEquals(3, def.levelFor(60));
		assertEquals(3, def.levelFor(1e9)); // capped at last level
		assertEquals(30.0, def.xpForLevel(2));
		assertEquals(60.0, def.xpForLevel(999)); // clamps into range
	}

	@Test
	void invalidTablesRejected() {
		assertThrows(IllegalArgumentException.class, () -> curve());
		assertThrows(IllegalArgumentException.class, () -> curve(0)); // single entry = level-0-only trap
		assertThrows(IllegalArgumentException.class, () -> curve(5, 10)); // first != 0
		assertThrows(IllegalArgumentException.class, () -> curve(0, 30, 20)); // not increasing
		assertThrows(IllegalArgumentException.class, () -> curve(0, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> curve(0, -5));
	}

	@Test
	void levelForHandlesDegenerateXp() {
		LevelCurveDefinition def = curve(0, 10, 30);
		assertEquals(0, def.levelFor(Double.NaN));
		assertEquals(0, def.levelFor(-100));
		assertEquals(2, def.levelFor(Double.POSITIVE_INFINITY)); // saturated, not 0
	}

	@Test
	void shippedDefaultCurveParsesAndIsSane() throws Exception {
		Path file = Path.of("src/main/resources/data/lifepath/skill/curve/default.json");
		assertTrue(Files.exists(file), "default.json must ship in the mod's datapack");
		var parsed = LevelCurveDefinition.LevelCurveFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
				.result().orElseThrow(() -> new AssertionError("default.json failed to parse"));
		LevelCurveDefinition def = LevelCurveDefinition.fromFile(ResourceLocation.fromNamespaceAndPath("lifepath", "default"), parsed);
		assertTrue(def.maxAttainableLevel() >= 100, "default curve must reach level 100");
		assertEquals(0, def.levelFor(0));
		assertTrue(def.levelFor(1e12) >= 100);
	}

	@Test
	void shippedDefaultCurveIsLinear() throws Exception {
		Path file = Path.of("src/main/resources/data/lifepath/skill/curve/default.json");
		var parsed = LevelCurveDefinition.LevelCurveFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
				.result().orElseThrow(() -> new AssertionError("default.json failed to parse"));
		// Linear design: the XP cost of each level is 40 + 5 * level — an
		// arithmetic ramp, never exponential, never flat.
		List<Double> t = parsed.thresholds();
		assertEquals(101, t.size(), "default curve must define levels 0-100");
		assertEquals(0.0, t.get(0));
		for (int level = 1; level <= 100; level++) {
			double delta = t.get(level) - t.get(level - 1);
			assertEquals(40.0 + 5.0 * level, delta, 1e-6,
					"level " + level + " cost must follow the linear ramp");
		}
	}

	@Test
	void missingCurveFallsBackGracefully() {
		LifepathContent.levelCurves().clear();
		// No curve registered — lookup warns once and falls back, never throws.
		int lvl = LevelCurves.levelFor(ResourceLocation.fromNamespaceAndPath("lifepath", "gone"), 1e9);
		assertTrue(lvl > 0); // fallback curve still produces levels
	}
}
