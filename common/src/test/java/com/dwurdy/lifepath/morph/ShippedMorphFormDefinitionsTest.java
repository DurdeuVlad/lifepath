package com.dwurdy.lifepath.morph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.content.MorphFormDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * M-6: guards the shipped {@code data/lifepath/morph_form/*.json} roster —
 * every file parses through {@code MorphFormFile.CODEC}, the handpicked
 * eight are all present (a dropped file silently shrinks the species-select
 * picker), and the vanilla-mirrored stat pins hold so a drive-by edit gets
 * a loud failure instead of a quiet balance drift.
 */
class ShippedMorphFormDefinitionsTest {
	private static final Path FORM_DIR =
			Path.of("src/main/resources/data/lifepath/morph_form");
	private static final List<String> ROSTER = List.of(
			"fox", "wolf", "cat", "rabbit", "goat", "panda", "polar_bear", "sheep");

	@BeforeAll
	static void bootMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static MorphFormDefinition.MorphFormFile parse(Path file) throws Exception {
		return MorphFormDefinition.MorphFormFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
				.result().orElseThrow(
						() -> new AssertionError(file.getFileName() + " failed to parse"));
	}

	@Test
	void everyShippedFormFileParses() throws Exception {
		try (Stream<Path> files = Files.list(FORM_DIR)) {
			List<Path> jsons = files.filter(p -> p.toString().endsWith(".json")).toList();
			assertEquals(ROSTER.size(), jsons.size(),
					"roster file count drifted: " + jsons);
			for (Path file : jsons) {
				parse(file);
			}
		}
	}

	@Test
	void handpickedRosterIsComplete() {
		for (String name : ROSTER) {
			assertTrue(Files.exists(FORM_DIR.resolve(name + ".json")),
					"missing shipped morph form " + name);
		}
	}

	@Test
	void statsMirrorVanillaProfiles() throws Exception {
		ResourceLocation hp =
				ResourceLocation.fromNamespaceAndPath("minecraft", "generic.max_health");
		ResourceLocation dmg =
				ResourceLocation.fromNamespaceAndPath("minecraft", "generic.attack_damage");
		ResourceLocation spd =
				ResourceLocation.fromNamespaceAndPath("minecraft", "generic.movement_speed");
		Map<String, double[]> pinned = Map.of(
				// name → {max_health, attack_damage, movement_speed}
				"fox", new double[]{10.0, 3.0, 0.30},
				"wolf", new double[]{8.0, 4.0, 0.30},
				"cat", new double[]{10.0, 3.0, 0.30},
				"rabbit", new double[]{3.0, 2.0, 0.35},
				"goat", new double[]{10.0, 2.0, 0.20},
				"panda", new double[]{20.0, 6.0, 0.15},
				"polar_bear", new double[]{30.0, 6.0, 0.25},
				"sheep", new double[]{8.0, 2.0, 0.23});
		for (var entry : pinned.entrySet()) {
			var file = parse(FORM_DIR.resolve(entry.getKey() + ".json"));
			assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", entry.getKey()),
					file.entityType(), entry.getKey() + " entity_type");
			double[] expected = entry.getValue();
			assertEquals(expected[0], file.stats().get(hp), 1e-6, entry.getKey() + " max_health");
			assertEquals(expected[1], file.stats().get(dmg), 1e-6, entry.getKey() + " attack_damage");
			assertEquals(expected[2], file.stats().get(spd), 1e-6, entry.getKey() + " movement_speed");
		}
	}
}
