package com.dwurdy.lifepath.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Morph feature M-1: {@code morph_form} files decode into the whitelist
 * registry — unknown entity types and unknown attribute ids fail the file
 * (fail closed), a non-positive max_health stat is rejected (proportional HP
 * carry divides by it), and malformed icons degrade icon-less like every
 * other domain (M12-1).
 */
class MorphFormDomainTest {

	@BeforeAll
	static void bootMinecraft() {
		// decodeMorphForm validates against vanilla registries — they must be
		// bootstrapped or the guard skips checks and the negative tests lie.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterEach
	void reset() {
		LifepathContent.morphForms().clear();
		// Icon warnings queue statically — drain so a sibling test's report
		// never sees this class's leftovers (order-dependent flake).
		com.dwurdy.lifepath.content.IconRef.drainWarnings();
	}

	private static int load(Map<ResourceLocation, com.google.gson.JsonElement> files) {
		return LifepathContent.registerAll("morph_form", files,
				com.dwurdy.lifepath.content.MorphFormDefinition.MorphFormFile.CODEC,
				LifepathContent::decodeMorphForm, LifepathContent.morphForms());
	}

	private static Map<ResourceLocation, com.google.gson.JsonElement> files(String name, String json) {
		Map<ResourceLocation, com.google.gson.JsonElement> files = new LinkedHashMap<>();
		files.put(ResourceLocation.fromNamespaceAndPath("lifepath", name), JsonParser.parseString(json));
		return files;
	}

	@Test
	void validFormLoadsAndIndexes() {
		int loaded = load(files("fox", """
				{
					"entity_type": "minecraft:fox",
					"display_name": "Fox",
					"description": "Small and quick.",
					"icon": "morph/fox",
					"stats": {
						"minecraft:generic.max_health": 10.0,
						"minecraft:generic.attack_damage": 2.0,
						"minecraft:generic.movement_speed": 0.3
					}
				}
				"""));

		ResourceLocation fox = ResourceLocation.fromNamespaceAndPath("lifepath", "fox");
		assertEquals(1, loaded);
		assertTrue(LifepathContent.morphForms().contains(fox));
		assertTrue(LifepathContent.exists("morph_form", fox));
		var def = LifepathContent.morphForms().get(fox);
		assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "fox"), def.entityType());
		assertEquals(10.0, def.stats()
				.get(ResourceLocation.fromNamespaceAndPath("minecraft", "generic.max_health")));
		assertEquals(java.util.Optional.of(
						ResourceLocation.fromNamespaceAndPath("lifepath", "textures/gui/morph/fox.png")),
				def.icon());
	}

	@Test
	void unknownEntityTypeSkipsFile() {
		int loaded = load(files("ghost", """
				{
					"entity_type": "minecraft:not_a_mob",
					"display_name": "Ghost"
				}
				"""));

		assertEquals(0, loaded);
		assertTrue(LifepathContent.morphForms().all().isEmpty());
	}

	@Test
	void unknownStatAttributeSkipsFile() {
		int loaded = load(files("fox", """
				{
					"entity_type": "minecraft:fox",
					"display_name": "Fox",
					"stats": { "minecraft:generic.bogus": 5.0 }
				}
				"""));

		assertEquals(0, loaded);
	}

	@Test
	void nonPositiveMaxHealthSkipsFile() {
		int loaded = load(files("fox", """
				{
					"entity_type": "minecraft:fox",
					"display_name": "Fox",
					"stats": { "minecraft:generic.max_health": 0.0 }
				}
				"""));

		assertEquals(0, loaded);
	}

	/**
	 * Contract pin: a non-land-animal category warns but LOADS — the category
	 * check is a lint for authors, the whitelist itself is the gate. Flying
	 * mobs are excluded by roster curation (#164), not by this check.
	 */
	@Test
	void nonAnimalCategoryWarnsButLoads() {
		int loaded = load(files("dragon", """
				{
					"entity_type": "minecraft:ender_dragon",
					"display_name": "Dragon"
				}
				"""));

		assertEquals(1, loaded,
				"non-animal categories warn-and-load — the authored whitelist is the real gate");
	}

	@Test
	void missingEntityTypeFailsDecode() {
		var result = com.dwurdy.lifepath.content.MorphFormDefinition.MorphFormFile.CODEC
				.parse(com.mojang.serialization.JsonOps.INSTANCE,
						JsonParser.parseString("{\"display_name\": \"Fox\"}"));

		assertTrue(result.error().isPresent());
	}

	@Test
	void malformedIconWarnsButFileLoads() {
		int loaded = load(files("fox", """
				{
					"entity_type": "minecraft:fox",
					"display_name": "Fox",
					"icon": "bad icon!!"
				}
				"""));

		assertEquals(1, loaded);
		assertTrue(LifepathContent.morphForms()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "fox"))
				.icon().isEmpty());
	}
}
