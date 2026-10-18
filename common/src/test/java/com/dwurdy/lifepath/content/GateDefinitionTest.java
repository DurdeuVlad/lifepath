package com.dwurdy.lifepath.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GateDefinitionTest {

	@Test
	void gateFileDecodesSkillOnly() {
		var file = GateDefinition.GateFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"skill\": \"lifepath:mining\"}"))
				.result().orElseThrow();

		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"),
				file.skill());
		assertTrue(file.breakGates().isEmpty());
		assertTrue(file.useGates().isEmpty());
		assertTrue(file.attackGates().isEmpty());
		assertTrue(file.interactGates().isEmpty());
		assertTrue(file.equipGates().isEmpty());
	}

	@Test
	void gateFileDecodesAllActions() {
		var file = GateDefinition.GateFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{
							"skill": "lifepath:defence",
							"break": {"lifepath:ore_tier_iron": 20},
							"use": {"lifepath:weapon_tier_iron": 20, "minecraft:shield": 10},
							"attack": {"lifepath:weapon_tier_diamond": 40},
							"interact": {"lifepath:husbandry_exotic": 30},
							"equip": {"lifepath:armor_tier_diamond": 50}
						}
						""")).result().orElseThrow();

		assertEquals(20, file.breakGates()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "ore_tier_iron")));
		assertEquals(2, file.useGates().size());
		assertEquals(40, file.attackGates()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "weapon_tier_diamond")));
		assertEquals(30, file.interactGates()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "husbandry_exotic")));
		assertEquals(50, file.equipGates()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "armor_tier_diamond")));
	}

	@Test
	void levelsOutsideRangeReject() {
		for (int level : new int[]{0, 101, -5}) {
			var parsed = GateDefinition.GateFile.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseString(
							"{\"skill\": \"lifepath:mining\", \"use\": {\"lifepath:t\": "
									+ level + "}}"));
			assertTrue(parsed.result().isEmpty(),
					"level " + level + " must not parse");
		}
	}

	@Test
	void fromFileRejectsAllEmptyMaps() {
		var file = GateDefinition.GateFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"skill\": \"lifepath:mining\"}"))
				.result().orElseThrow();
		assertThrows(IllegalArgumentException.class, () -> GateDefinition.fromFile(
				ResourceLocation.fromNamespaceAndPath("lifepath", "mining"), file));
	}

	@Test
	void fromFileKeepsEntries() {
		var file = GateDefinition.GateFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString(
						"{\"skill\": \"lifepath:mining\", \"break\": {\"lifepath:t\": 20}}"))
				.result().orElseThrow();
		var def = GateDefinition.fromFile(
				ResourceLocation.fromNamespaceAndPath("lifepath", "mining"), file);
		assertEquals(20, def.breakGates()
				.get(ResourceLocation.fromNamespaceAndPath("lifepath", "t")));
	}
}
