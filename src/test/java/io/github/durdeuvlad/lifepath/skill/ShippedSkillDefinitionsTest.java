package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the shipped datapack: every {@code data/lifepath/skill/*.json} must
 * parse through {@code SkillDefinitionFile.CODEC}. A malformed shipping file
 * otherwise fails silently — the loader logs and skips it, so the skill simply
 * never exists in-game.
 */
class ShippedSkillDefinitionsTest {
	private static final Path SKILL_DIR = Path.of("src/main/resources/data/lifepath/skill");
	private static final Map<String, String> REQUIRED = Map.of(
			"mining", "gathering",
			"farming", "gathering",
			"smithing", "crafting",
			"fishing", "gathering");

	@Test
	void everyShippedSkillFileParses() throws Exception {
		try (Stream<Path> files = Files.list(SKILL_DIR)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				var parsed = SkillDefinition.SkillDefinitionFile.CODEC
						.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)));
				assertTrue(parsed.result().isPresent(),
						file.getFileName() + " must parse: " + parsed.error().orElse(null));
			}
		}
	}

	@Test
	void fourRequiredSkillsExistWithCorrectCategories() throws Exception {
		for (Map.Entry<String, String> e : REQUIRED.entrySet()) {
			Path file = SKILL_DIR.resolve(e.getKey() + ".json");
			assertTrue(Files.exists(file), "missing shipped skill " + file);
			var def = SkillDefinition.SkillDefinitionFile.CODEC
					.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
					.result().orElseThrow(() -> new AssertionError(file + " failed to parse"));
			assertEquals(e.getValue(), def.category().name().toLowerCase(java.util.Locale.ROOT),
					e.getKey() + " category");
			assertEquals(100, def.maxLevel(), e.getKey() + " maxLevel");
		}
	}
}
