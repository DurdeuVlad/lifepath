package com.dwurdy.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.skill.SkillProgress;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M4-2 condition vocabulary: data-path primitives are exercised fully;
 * entity/world primitives are verified to fail closed with no player (the
 * live-entity reads are thin wrappers over vanilla accessors — in-game
 * verification is deferred like the rest of the player-dependent surface).
 */
class BuiltinConditionsTest {
	private PlayerCharacterData data;
	private AbilityVocabulary.EvalContext ctx;

	@BeforeEach
	void setUp() {
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init(); // registers the full built-in vocabulary
		data = PlayerCharacterData.createDefault();
		ctx = new AbilityVocabulary.EvalContext(null, data, 0L);
	}

	private static com.google.gson.JsonObject params(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	private static AbilityVocabulary.ConditionEvaluator cond(String name) {
		return AbilityVocabulary.condition(LifepathMod.id(name));
	}

	@Test
	void allFourteenPrimitivesAreRegistered() {
		for (String name : new String[] {"biome_tag", "dimension", "block_nearby",
				"entity_nearby", "daylight", "night", "health_threshold",
				"inventory_contains", "equipment_contains", "submerged", "on_fire",
				"weather", "skill_level", "resource_threshold"}) {
			assertTrue(cond(name) != null, "missing condition " + name);
		}
	}

	@Test
	void worldConditionsFailClosedWithoutAPlayer() {
		for (String name : new String[] {"biome_tag", "dimension", "block_nearby",
				"entity_nearby", "daylight", "night", "health_threshold",
				"food_level", "inventory_contains", "equipment_contains",
				"submerged", "on_fire", "is_flying", "weather", "exposed_to_sky",
				"in_precipitation", "light_level", "y_level"}) {
			// Params shaped as if real — the contract is "no player → false",
			// regardless of what the params ask for.
			assertFalse(cond(name).test(ctx, params("""
					{"tag": "minecraft:is_forest", "id": "minecraft:overworld",
					 "block": "minecraft:stone", "entity": "minecraft:zombie",
					 "item": "minecraft:diamond", "op": "gte", "value": 10,
					 "level": 5, "radius": 8, "state": "clear", "source": "any"}
					""")), name + " must fail closed without a player");
		}
	}

	@Test
	void m14WorldProbesAreRegistered() {
		for (String name : new String[] {"exposed_to_sky", "in_precipitation",
				"light_level", "y_level"}) {
			assertTrue(cond(name) != null, "missing condition " + name);
		}
		// in_precipitation's "state" and light_level's "source" keys read
		// raw params — malformed values must not throw or pass.
		assertFalse(cond("in_precipitation").test(ctx, params("{\"state\": 3}")));
		assertFalse(cond("light_level").test(ctx, params("{\"op\": \"gte\"}"))); // no value
		assertFalse(cond("y_level").test(ctx, params("{\"value\": 60}"))); // no op
		assertFalse(cond("food_level").test(ctx, params("{\"op\": \"lt\"}"))); // no value
	}

	@Test
	void victimEntityFailsClosedOffTheDealtPath() {
		// victim_entity only resolves inside a damage_dealt eval — no damage
		// context, or a taken-style context with no victim, both fail closed.
		assertFalse(cond("victim_entity").test(ctx,
				params("{\"entity\": \"minecraft:zombie\"}")));
		var takenCtx = new AbilityVocabulary.EvalContext(null, data, 0L, null,
				new AbilityVocabulary.DamageInfo(null, null, 4f));
		assertFalse(cond("victim_entity").test(takenCtx,
				params("{\"entity\": \"minecraft:zombie\"}")));
	}

	@Test
	void skillLevelComparesAgainstProgress() {
		ResourceLocation skill = LifepathMod.id("mining");
		data.setSkillProgress(skill, new SkillProgress(0, 12, 12, 0, null, 0));
		assertTrue(cond("skill_level").test(ctx,
				params("{\"skill\": \"lifepath:mining\", \"op\": \"gte\", \"level\": 10}")));
		assertFalse(cond("skill_level").test(ctx,
				params("{\"skill\": \"lifepath:mining\", \"op\": \"lt\", \"level\": 10}")));
		// Untracked skill counts as level 0.
		assertTrue(cond("skill_level").test(ctx,
				params("{\"skill\": \"lifepath:untrained\", \"op\": \"eq\", \"level\": 0}")));
		// Missing params fail closed.
		assertFalse(cond("skill_level").test(ctx, params("{\"op\": \"gte\"}")));
	}

	@Test
	void resourceThresholdComparesCurrent() {
		data.setResource(LifepathMod.id("mana"),
				new PlayerCharacterData.ResourceState(30.0, 0, 100));
		assertTrue(cond("resource_threshold").test(ctx,
				params("{\"resource\": \"lifepath:mana\", \"op\": \">\", \"value\": 20}")));
		assertFalse(cond("resource_threshold").test(ctx,
				params("{\"resource\": \"lifepath:mana\", \"op\": \"lte\", \"value\": 20}")));
		// Missing resource fails closed rather than pretending 0.
		assertFalse(cond("resource_threshold").test(ctx,
				params("{\"resource\": \"lifepath:none\", \"op\": \"gte\", \"value\": 0}")));
	}

	@Test
	void compareSupportsAllOperators() {
		assertTrue(BuiltinConditions.compare("gt", 5, 3));
		assertTrue(BuiltinConditions.compare("gte", 5, 5));
		assertTrue(BuiltinConditions.compare("lt", 2, 3));
		assertTrue(BuiltinConditions.compare("lte", 3, 3));
		assertTrue(BuiltinConditions.compare("eq", 4, 4));
		assertTrue(BuiltinConditions.compare("neq", 4, 3));
		assertTrue(BuiltinConditions.compare(">=", 5, 5));
		assertTrue(BuiltinConditions.compare("!=", 1, 2));
		assertFalse(BuiltinConditions.compare("bogus", 5, 5)); // unknown op fails closed
	}

	@Test
	void radiusClampsToConfigMax() {
		// abilities.toml absent in tests → getOrDefault yields the default 32.
		assertEquals(32, BuiltinConditions.radius(params("{\"radius\": 999}")));
		assertEquals(8, BuiltinConditions.radius(params("{\"radius\": 8}")));
		assertEquals(8, BuiltinConditions.radius(params("{}"))); // default radius
		assertEquals(0, BuiltinConditions.radius(params("{\"radius\": -3}")));
	}

	@Test
	void malformedIdOrTagParamsFailClosed() {
		// Wrong types for the param keys must not throw.
		assertFalse(cond("skill_level").test(ctx,
				params("{\"skill\": {\"x\": 1}, \"op\": \"gte\", \"level\": 1}")));
		assertFalse(cond("resource_threshold").test(ctx,
				params("{\"resource\": \"lifepath:mana\", \"op\": [1], \"value\": 1}")));
		assertFalse(cond("weather").test(ctx,
				params("{\"state\": 7}")));
	}

	@Test
	void unknownSpecNodeTypesFailTheFileAtLoad() {
		// M4-2 contract: an unknown condition type is a load error naming the
		// ability — never a silent fail-closed. Exercises the production
		// decoder through registerAll (the real datapack-load path).
		var files = new java.util.LinkedHashMap<ResourceLocation, com.google.gson.JsonElement>();
		files.put(LifepathMod.id("bad"), JsonParser.parseString("""
				{"display_name": "Bad", "trigger": {"type": "active"},
				 "conditions": {"all": [{"type": "lifepath:not_a_condition"}]},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:debug_log"}]}
				"""));
		files.put(LifepathMod.id("good"), JsonParser.parseString("""
				{"display_name": "Good", "trigger": {"type": "active"},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:debug_log"}]}
				"""));
		LifepathContent.abilities().clear();
		int loaded = LifepathContent.registerAll("ability", files,
				AbilityDefinition.AbilityFile.CODEC, LifepathContent::decodeAbility,
				LifepathContent.abilities());
		assertEquals(1, loaded);
		assertTrue(LifepathContent.abilities().contains(LifepathMod.id("good")));
		assertFalse(LifepathContent.abilities().contains(LifepathMod.id("bad")));
	}

	@Test
	void unknownActionAndTargetTypesAreAlsoCaught() {
		AbilityDefinition.AbilityFile file = AbilityDefinition.AbilityFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "Bad2", "trigger": {"type": "active"},
						 "target": {"type": "lifepath:not_a_target"},
						 "actions": [{"type": "lifepath:not_an_action"}]}
						""")).result().orElseThrow();
		var unknown = AbilityVocabulary.unknownNodeTypes(
				AbilityDefinition.fromFile(LifepathMod.id("bad2"), file));
		assertTrue(unknown.contains(LifepathMod.id("not_a_target")));
		assertTrue(unknown.contains(LifepathMod.id("not_an_action")));
	}

	@Test
	void knownTypesPassValidation() {
		AbilityDefinition.AbilityFile file = AbilityDefinition.AbilityFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "Ok", "trigger": {"type": "active"},
						 "conditions": {"all": [
						   {"type": "lifepath:skill_level", "skill": "lifepath:mining",
						    "op": "gte", "level": 10},
						   {"type": "lifepath:resource_threshold",
						    "resource": "lifepath:mana", "op": ">", "value": 5}],
						  "any": [{"type": "lifepath:always"}]},
						 "target": {"type": "lifepath:self"},
						 "actions": [{"type": "lifepath:debug_log", "message": "x"}]}
						""")).result().orElseThrow();
		assertTrue(AbilityVocabulary.unknownNodeTypes(
				AbilityDefinition.fromFile(LifepathMod.id("ok"), file)).isEmpty());
	}

	@Test
	void validationToleratesUninitializedVocabulary() {
		// Data-path harnesses that never init the vocabulary (e.g. plain
		// registry tests) must not have every file rejected.
		AbilityVocabulary.resetForTests();
		AbilityDefinition.AbilityFile file = AbilityDefinition.AbilityFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "X", "trigger": {"type": "passive"},
						 "target": {"type": "any:thing"}, "actions": []}
						""")).result().orElseThrow();
		assertTrue(AbilityVocabulary.unknownNodeTypes(
				AbilityDefinition.fromFile(LifepathMod.id("x"), file)).isEmpty());
	}

	@Test
	void allAnyCompositionEndToEnd() {
		// Engine-level proof the data shape composes: every `all` must pass AND
		// (when present) at least one `any` must pass.
		ResourceLocation skill = LifepathMod.id("mining");
		data.setSkillProgress(skill, new SkillProgress(0, 20, 20, 0, null, 0));
		data.setResource(LifepathMod.id("mana"),
				new PlayerCharacterData.ResourceState(1.0, 0, 100));
		AbilityDefinition.AbilityFile file = AbilityDefinition.AbilityFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "Composed", "trigger": {"type": "active"},
						 "conditions": {"all": [
						   {"type": "lifepath:skill_level", "skill": "lifepath:mining",
						    "op": "gte", "level": 10}],
						  "any": [
						   {"type": "lifepath:resource_threshold",
						    "resource": "lifepath:mana", "op": "gte", "value": 50},
						   {"type": "lifepath:skill_level",
						    "skill": "lifepath:mining", "op": "gte", "level": 15}]},
						 "target": {"type": "lifepath:self"},
						 "actions": [{"type": "lifepath:debug_log"}]}
						""")).result().orElseThrow();
		AbilityDefinition def = AbilityDefinition.fromFile(LifepathMod.id("composed"), file);
		LifepathContent.abilities().clear();
		LifepathContent.abilities().register(def.id(), def);
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, def.id());
		// all passes (level 20 >= 10); any passes via the second arm (level >= 15
		// — mana 1 < 50 fails the first) → executes.
		assertEquals(AbilityEngine.Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, def.id(), 1_000L));
	}
}
