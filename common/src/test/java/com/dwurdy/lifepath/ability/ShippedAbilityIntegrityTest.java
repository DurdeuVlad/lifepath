package com.dwurdy.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.ResourceDefinition;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the shipped {@code data/lifepath/ability/*.json} set: every file must
 * decode through the production path (vocabulary types + content refs) and
 * every registry-backed reference must resolve against the bootstrapped
 * vanilla registries. Registry refs are validated lazily by the evaluators
 * at runtime, so an id typo (e.g. {@code generic.sneaking_speed} instead of
 * {@code player.sneaking_speed}) silently no-ops the ability in-game — this
 * test makes that failure loud at build time.
 */
class ShippedAbilityIntegrityTest {
	private static final Path DATA_DIR = Path.of("src/main/resources/data/lifepath");
	private static final Path ABILITY_DIR = DATA_DIR.resolve("ability");
	private static final Path SKILL_DIR = DATA_DIR.resolve("skill");
	private static final Path RESOURCE_DIR = DATA_DIR.resolve("resource");

	/**
	 * Param names that hold vanilla-registry ids, by registry. Built lazily —
	 * touching {@link BuiltInRegistries} before {@link Bootstrap#bootStrap()}
	 * throws.
	 */
	private static Map<String, Registry<?>> registryKeys() {
		return Map.of(
				"attribute", BuiltInRegistries.ATTRIBUTE,
				"effect", BuiltInRegistries.MOB_EFFECT,
				"item", BuiltInRegistries.ITEM,
				"block", BuiltInRegistries.BLOCK,
				"entity", BuiltInRegistries.ENTITY_TYPE,
				"sound", BuiltInRegistries.SOUND_EVENT);
	}

	@BeforeAll
	static void bootMinecraft() throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		LifepathContent.skills().clear();
		LifepathContent.resources().clear();
		loadShippedContent();
	}

	@AfterAll
	static void resetState() {
		LifepathContent.skills().clear();
		LifepathContent.resources().clear();
		AbilityVocabulary.resetForTests();
	}

	private static void loadShippedContent() throws Exception {
		try (Stream<Path> files = Files.list(SKILL_DIR)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				var parsed = SkillDefinition.SkillDefinitionFile.CODEC
						.parse(JsonOps.INSTANCE,
								JsonParser.parseString(Files.readString(file)))
						.result().orElseThrow(
								() -> new AssertionError(file + " failed to parse"));
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lifepath",
						file.getFileName().toString().replace(".json", ""));
				LifepathContent.skills().register(id, SkillDefinition.fromFile(id, parsed));
			}
		}
		try (Stream<Path> files = Files.list(RESOURCE_DIR)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				var parsed = ResourceDefinition.ResourceFile.CODEC
						.parse(JsonOps.INSTANCE,
								JsonParser.parseString(Files.readString(file)))
						.result().orElseThrow(
								() -> new AssertionError(file + " failed to parse"));
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lifepath",
						file.getFileName().toString().replace(".json", ""));
				LifepathContent.resources().register(id,
						LifepathContent.decodeResource(id, parsed));
			}
		}
	}

	private static List<Path> abilityFiles() throws Exception {
		try (Stream<Path> files = Files.list(ABILITY_DIR)) {
			return files.filter(p -> p.toString().endsWith(".json")).toList();
		}
	}

	@Test
	void everyShippedAbilityDecodesThroughProductionPath() throws Exception {
		List<Path> files = abilityFiles();
		assertTrue(files.size() > 500,
				"ability roster shrank unexpectedly: " + files.size());
		for (Path file : files) {
			var parsed = AbilityDefinition.AbilityFile.CODEC
					.parse(JsonOps.INSTANCE,
							JsonParser.parseString(Files.readString(file)))
					.result().orElseThrow(
							() -> new AssertionError(file + " failed to parse"));
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lifepath",
					file.getFileName().toString().replace(".json", ""));
			try {
				LifepathContent.decodeAbility(id, parsed);
			} catch (IllegalArgumentException e) {
				fail(file.getFileName() + " failed decode validation: " + e.getMessage());
			}
		}
	}

	@Test
	void everyRegistryRefResolvesAgainstVanilla() throws Exception {
		Map<String, Registry<?>> keys = registryKeys();
		for (Path file : abilityFiles()) {
			JsonObject root = JsonParser.parseString(Files.readString(file))
					.getAsJsonObject();
			checkRefs(root, file.getFileName().toString(), keys);
		}
	}

	private static void checkRefs(JsonElement node, String file,
			Map<String, Registry<?>> keys) {
		if (node instanceof JsonObject obj) {
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				Registry<?> registry = keys.get(e.getKey());
				JsonElement value = e.getValue();
				if (registry != null && value.isJsonPrimitive()
						&& value.getAsJsonPrimitive().isString()) {
					String ref = value.getAsString();
					if (!ref.startsWith("#")) {
						ResourceLocation id = ResourceLocation.parse(ref);
						assertTrue(registry.getOptional(id).isPresent(),
								file + ": unknown registry id \"" + ref + "\" in \""
										+ e.getKey() + "\" — the evaluator silently "
										+ "no-ops on unresolved ids");
					}
				}
				checkRefs(value, file, keys);
			}
		} else if (node instanceof com.google.gson.JsonArray arr) {
			for (JsonElement e : arr) {
				checkRefs(e, file, keys);
			}
		}
	}
}
