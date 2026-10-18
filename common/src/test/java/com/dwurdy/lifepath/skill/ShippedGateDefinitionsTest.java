package com.dwurdy.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.content.GateDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Guards the shipped skill-unlock gates: every {@code data/lifepath/gate/*.json}
 * must parse, reference a real skill, declare at least one action entry, and
 * point at tag files that actually exist for that action's registry type
 * ({@code break} -> block tags, {@code use}/{@code attack} -> item tags,
 * {@code interact} -> entity_type tags). Also asserts coverage: every shipped
 * skill gets a gate file.
 */
class ShippedGateDefinitionsTest {
	private static final Path GATE_DIR = Path.of("src/main/resources/data/lifepath/gate");
	private static final Path SKILL_DIR = Path.of("src/main/resources/data/lifepath/skill");
	private static final Path TAG_ROOT = Path.of("src/main/resources/data/lifepath/tags");

	private static final Map<String, String> ACTION_TAG_DIR = Map.of(
			"break", "block", "use", "item", "attack", "item", "interact", "entity_type");

	private static GateDefinition.GateFile decode(Path file) {
		return GateDefinition.GateFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(read(file)))
				.result().orElseThrow(() -> new AssertionError(file + " failed to parse"));
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (java.io.IOException e) {
			throw new AssertionError(file + " unreadable", e);
		}
	}

	private static Stream<Path> jsonFiles(Path dir) throws Exception {
		return Files.list(dir).filter(p -> p.toString().endsWith(".json"));
	}

	@Test
	void everyShippedGateFileParsesAndDeclaresEntries() throws Exception {
		try (Stream<Path> files = jsonFiles(GATE_DIR)) {
			for (Path file : files.toList()) {
				var parsed = decode(file);
				var def = assertDoesNotThrow(() -> GateDefinition.fromFile(
						ResourceLocation.fromNamespaceAndPath("lifepath",
								file.getFileName().toString().replace(".json", "")),
						parsed), file + " must declare at least one action gate");
				assertTrue(def.skill() != null, file + " must have a skill");
			}
		}
	}

	@Test
	void everyGateSkillRefResolves() throws Exception {
		Set<String> skillIds;
		try (Stream<Path> files = jsonFiles(SKILL_DIR)) {
			skillIds = files.map(f -> f.getFileName().toString().replace(".json", ""))
					.collect(java.util.stream.Collectors.toSet());
		}
		try (Stream<Path> files = jsonFiles(GATE_DIR)) {
			for (Path file : files.toList()) {
				var parsed = decode(file);
				assertTrue(skillIds.contains(parsed.skill().getPath()),
						file + " references unknown skill " + parsed.skill());
			}
		}
	}

	@Test
	void everySkillHasAGateFile() throws Exception {
		Set<String> gateIds;
		try (Stream<Path> files = jsonFiles(GATE_DIR)) {
			gateIds = files.map(f -> f.getFileName().toString().replace(".json", ""))
					.collect(java.util.stream.Collectors.toSet());
		}
		try (Stream<Path> files = jsonFiles(SKILL_DIR)) {
			for (Path file : files.filter(f -> !f.getFileName().toString()
					.equals("default.json")).toList()) {
				String id = file.getFileName().toString().replace(".json", "");
				assertTrue(gateIds.contains(id),
						"skill " + id + " has no data/lifepath/gate/" + id + ".json");
			}
		}
	}

	@Test
	void lifepathTagRefsExistInTheRightRegistry() throws Exception {
		try (Stream<Path> files = jsonFiles(GATE_DIR)) {
			for (Path file : files.toList()) {
				var parsed = decode(file);
				for (var action : ACTION_TAG_DIR.entrySet()) {
					Map<ResourceLocation, Integer> map = switch (action.getKey()) {
						case "break" -> parsed.breakGates();
						case "use" -> parsed.useGates();
						case "attack" -> parsed.attackGates();
						default -> parsed.interactGates();
					};
					for (ResourceLocation tag : map.keySet()) {
						if (!tag.getNamespace().equals("lifepath")) {
							continue;
						}
						Path tagFile = TAG_ROOT.resolve(action.getValue())
								.resolve(tag.getPath() + ".json");
						assertTrue(Files.exists(tagFile),
								file + "." + action.getKey() + " refs missing tag file "
										+ tagFile);
					}
				}
			}
		}
	}
}
