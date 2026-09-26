package io.github.durdeuvlad.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityEngine.Outcome;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M4-1 engine contract: all three trigger kinds execute end-to-end on
 * data-defined abilities, active activation is fully server-validated, and a
 * new species mechanic is expressible as pure composition — no Java per
 * ability. Vocabulary primitives here are test-registered stand-ins; the real
 * condition/action vocabularies land in M4-2/M4-3.
 */
class AbilityEngineTest {
	private static final Identifier SPECIES = LifepathMod.id("test_species");
	private static final Identifier ABILITY = LifepathMod.id("test_ability");
	private static final Identifier EVENT_TYPE = LifepathMod.id("mine_block");

	private AtomicInteger fired;

	@BeforeEach
	void setUp() {
		LifepathContent.abilities().clear();
		LifepathContent.species().clear();
		AbilityVocabulary.resetForTests();
		fired = new AtomicInteger();
		// Test vocabulary: a flag-flipping action + a param-controlled condition.
		AbilityVocabulary.registerAction(LifepathMod.id("test_fire"),
				(target, ctx, params) -> fired.incrementAndGet());
		AbilityVocabulary.registerTarget(LifepathMod.id("self"), (ctx, params) ->
				List.of(new AbilityVocabulary.TargetContext(null, ctx.data())));
		AbilityVocabulary.registerCondition(LifepathMod.id("test_flag"), (ctx, params) ->
				params.has("value") && params.get("value").getAsBoolean());
		AbilityVocabulary.init(); // built-ins: always / has_resource / self / grant_xp / ...
	}

	private static void registerAbility(String json) {
		AbilityDefinition.AbilityFile file = AbilityDefinition.AbilityFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
				.result().orElseThrow();
		LifepathContent.abilities().register(ABILITY,
				AbilityDefinition.fromFile(ABILITY, file));
	}

	private static void speciesWith(List<Identifier> passive, List<Identifier> active) {
		LifepathContent.species().register(SPECIES, new SpeciesDefinition(
				SPECIES, "Test", SpeciesDefinition.Visibility.NORMAL,
				SpeciesDefinition.Selection.OPEN, passive, active,
				java.util.Map.of(), List.of(),
				java.util.Optional.empty(), java.util.Optional.empty()));
	}

	private static PlayerCharacterData characterOfSpecies() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(SPECIES);
		return data;
	}

	private static final String PASSIVE_JSON = """
			{"display_name": "Passive", "trigger": {"type": "passive", "interval_ticks": 20},
			 "target": {"type": "lifepath:self"},
			 "actions": [{"type": "lifepath:test_fire"}]}
			""";

	@Test
	void passiveSweepExecutesAndIntervalGates() {
		registerAbility(PASSIVE_JSON);
		speciesWith(List.of(ABILITY), List.of());
		PlayerCharacterData data = characterOfSpecies();

		AbilityEngine.runPassiveSweep(data, null, 1_000L);
		assertEquals(1, fired.get());

		// Interval gate: within interval_ticks the marker suppresses re-eval.
		AbilityEngine.runPassiveSweep(data, null, 1_500L);
		assertEquals(1, fired.get());

		// Past interval (20 ticks = 1000ms): re-evaluates.
		AbilityEngine.runPassiveSweep(data, null, 2_100L);
		assertEquals(2, fired.get());
	}

	@Test
	void activeActivationExecutesForOwner() {
		registerAbility("""
				{"display_name": "Active", "trigger": {"type": "active"},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		PlayerCharacterData data = characterOfSpecies();

		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(1, fired.get());
	}

	@Test
	void forgedPacketForNonOwnedAbilityDoesNothing() {
		registerAbility("""
				{"display_name": "Active", "trigger": {"type": "active"},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		PlayerCharacterData data = PlayerCharacterData.createDefault(); // no species

		assertEquals(Outcome.NOT_OWNED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(0, fired.get());
	}

	@Test
	void activationRejectsPassiveAndUnknown() {
		registerAbility(PASSIVE_JSON);
		speciesWith(List.of(ABILITY), List.of());
		PlayerCharacterData data = characterOfSpecies();

		assertEquals(Outcome.WRONG_TRIGGER,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(Outcome.NOT_OWNED,
				AbilityEngine.tryActivate(data, null, LifepathMod.id("absent"), 1_000L));
	}

	@Test
	void eventAbilityFiresOnlyOnListedEventTypes() {
		registerAbility("""
				{"display_name": "Event", "trigger": {"type": "event",
				 "events": ["lifepath:mine_block"]},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(ABILITY), List.of());
		PlayerCharacterData data = characterOfSpecies();

		AbilityEngine.handleEvent(data, null, LifepathMod.id("unrelated"), 1_000L);
		assertEquals(0, fired.get());

		AbilityEngine.handleEvent(data, null, EVENT_TYPE, 1_000L);
		assertEquals(1, fired.get());
	}

	@Test
	void allOfConditionsMustAllPass() {
		registerAbility("""
				{"display_name": "AllOf", "trigger": {"type": "active"},
				 "conditions": {"all": [
				   {"type": "lifepath:test_flag", "value": true},
				   {"type": "lifepath:test_flag", "value": false}]},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		PlayerCharacterData data = characterOfSpecies();

		assertEquals(Outcome.CONDITIONS_FAILED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(0, fired.get());
	}

	@Test
	void anyOfConditionsNeedOnePass() {
		registerAbility("""
				{"display_name": "AnyOf", "trigger": {"type": "active"},
				 "conditions": {"any": [
				   {"type": "lifepath:test_flag", "value": false},
				   {"type": "lifepath:test_flag", "value": true}]},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		PlayerCharacterData data = characterOfSpecies();

		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(1, fired.get());
	}

	@Test
	void unknownConditionFailsClosedAndUnknownActionIsNoOp() {
		registerAbility("""
				{"display_name": "Unknowns", "trigger": {"type": "active"},
				 "conditions": {"all": [{"type": "lifepath:no_such_condition"}]},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		PlayerCharacterData data = characterOfSpecies();
		assertEquals(Outcome.CONDITIONS_FAILED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));

		// Unknown action: executes, logs, does not crash.
		LifepathContent.abilities().clear();
		LifepathContent.abilities().register(ABILITY, AbilityDefinition.fromFile(ABILITY,
				AbilityDefinition.AbilityFile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "NoOp", "trigger": {"type": "active"},
						 "target": {"type": "lifepath:self"},
						 "actions": [{"type": "lifepath:no_such_action"}]}
						""")).result().orElseThrow()));
		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(0, fired.get());
	}

	@Test
	void costGateSpendsOnSuccessAndBlocksWhenShort() {
		Identifier stamina = LifepathMod.id("stamina");
		registerAbility("""
				{"display_name": "Costly", "trigger": {"type": "active"},
				 "cost": {"resource": "lifepath:stamina", "amount": 5.0},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		PlayerCharacterData data = characterOfSpecies();
		data.setResource(stamina, new PlayerCharacterData.ResourceState(3.0, 0, 100));
		assertEquals(Outcome.COST_UNMET,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(0, fired.get());

		data.setResource(stamina, new PlayerCharacterData.ResourceState(10.0, 0, 100));
		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(1, fired.get());
		assertEquals(5.0, data.resources().get(stamina).current());
	}

	@Test
	void cooldownBlocksImmediateReactivation() {
		registerAbility("""
				{"display_name": "Cooled", "trigger": {"type": "active"},
				 "cooldown": {"seconds": 30.0},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		PlayerCharacterData data = characterOfSpecies();

		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(Outcome.ON_COOLDOWN,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_500L));
		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, ABILITY, 32_000L));
		assertEquals(2, fired.get());
	}

	@Test
	void missingAndStaleReferencesSkipGracefully() {
		// Species points at an ability file that was deleted — engine skips.
		speciesWith(List.of(ABILITY), List.of());
		PlayerCharacterData data = characterOfSpecies();
		AbilityEngine.runPassiveSweep(data, null, 1_000L); // no def: no throw, no fire
		assertEquals(0, fired.get());
		assertEquals(Outcome.NOT_OWNED,
				AbilityEngine.tryActivate(data, null, LifepathMod.id("gone"), 1_000L));
	}

	@Test
	void specializationSignatureRefsGrantOwnership() {
		registerAbility("""
				{"display_name": "Signature", "trigger": {"type": "active"},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		var specId = LifepathMod.id("test_spec");
		LifepathContent.specializations().register(specId,
				new io.github.durdeuvlad.lifepath.content.SpecializationDefinition(
						specId, "Spec", java.util.Map.of(), java.util.Map.of(),
						java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
						List.of(ABILITY)));
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpecializationId(specId);

		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, ABILITY, 1_000L));
		assertEquals(1, fired.get());
	}

	/**
	 * Engine health test: a hypothetical new species mechanic ("adrenaline
	 * surge" — on sprint-start, gain stamina) expressed ENTIRELY as data +
	 * existing vocabulary primitives. No Java was touched to support it.
	 */
	@Test
	void hypotheticalSpeciesMechanicIsPureData() {
		Identifier surge = LifepathMod.id("adrenaline_surge");
		AbilityDefinition.AbilityFile file = AbilityDefinition.AbilityFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "Adrenaline Surge",
						 "trigger": {"type": "event", "events": ["lifepath:sprint_start"]},
						 "conditions": {"all": [{"type": "lifepath:always"}]},
						 "target": {"type": "lifepath:self"},
						 "actions": [{"type": "lifepath:resource_delta",
						              "resource": "lifepath:stamina", "amount": 10.0}]}
						""")).result().orElseThrow();
		LifepathContent.abilities().register(surge, AbilityDefinition.fromFile(surge, file));
		speciesWith(List.of(surge), List.of());
		PlayerCharacterData data = characterOfSpecies();
		Identifier stamina = LifepathMod.id("stamina");
		data.setResource(stamina, new PlayerCharacterData.ResourceState(50.0, 0, 100));

		AbilityEngine.handleEvent(data, null, LifepathMod.id("sprint_start"), 1_000L);
		assertEquals(60.0, data.resources().get(stamina).current());
	}

	@Test
	void targetResolverMayYieldMultipleTargets() {
		// AoE shape: the action loop runs once per resolved target — the
		// resolver decides "who", the engine just iterates.
		AbilityVocabulary.registerTarget(LifepathMod.id("test_area"), (ctx, params) ->
				java.util.stream.IntStream.range(0, 3)
						.mapToObj(i -> new AbilityVocabulary.TargetContext(null, ctx.data()))
						.toList());
		registerAbility("""
				{"display_name": "Area", "trigger": {"type": "active"},
				 "target": {"type": "lifepath:test_area", "radius": 4},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		assertEquals(Outcome.EXECUTED,
				AbilityEngine.tryActivate(characterOfSpecies(), null, ABILITY, 1_000L));
		assertEquals(3, fired.get());
	}

	@Test
	void noTargetsResolvedSkipsExecution() {
		AbilityVocabulary.registerTarget(LifepathMod.id("test_none"), (ctx, params) -> List.of());
		registerAbility("""
				{"display_name": "Empty", "trigger": {"type": "active"},
				 "target": {"type": "lifepath:test_none"},
				 "actions": [{"type": "lifepath:test_fire"}]}
				""");
		speciesWith(List.of(), List.of(ABILITY));
		assertEquals(Outcome.NO_TARGETS,
				AbilityEngine.tryActivate(characterOfSpecies(), null, ABILITY, 1_000L));
		assertEquals(0, fired.get());
	}

	@Test
	void resourceInteractionsClampAndAccumulate() {
		Identifier stamina = LifepathMod.id("stamina");
		registerAbility("""
				{"display_name": "Regen", "trigger": {"type": "passive", "interval_ticks": 20},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:test_fire"}],
				 "resource_interactions": [{"resource": "lifepath:stamina", "per_second": 10.0}]}
				""");
		speciesWith(List.of(ABILITY), List.of());
		PlayerCharacterData data = characterOfSpecies();
		data.setResource(stamina, new PlayerCharacterData.ResourceState(99.0, 0, 100));

		// 20 ticks = 1s → +10, clamped to max 100.
		AbilityEngine.runPassiveSweep(data, null, 1_000L);
		assertEquals(100.0, data.resources().get(stamina).current());
	}

	@Test
	void specNodePreservesRawParameters() {
		var node = AbilityDefinition.SpecNode.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{"type": "lifepath:biome_check", "biome": "minecraft:plains",
						 "nested": {"radius": 3}}
						""")).result().orElseThrow();
		assertEquals(LifepathMod.id("biome_check"), node.type());
		assertEquals("minecraft:plains", node.raw().get("biome").getAsString());
		assertEquals(3, node.raw().getAsJsonObject("nested").get("radius").getAsInt());

		// Missing/invalid 'type' fails at parse time — malformed never loads.
		assertTrue(AbilityDefinition.SpecNode.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"biome\": \"x\"}")).isError());
		assertTrue(AbilityDefinition.SpecNode.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"type\": \"!!not-an-id\"}")).isError());
	}

	@Test
	void malformedAbilityFileSkipsWithoutCorruptingRegistry() {
		java.util.Map<Identifier, com.google.gson.JsonElement> files = new java.util.LinkedHashMap<>();
		files.put(LifepathMod.id("good"), JsonParser.parseString("""
				{"display_name": "Good", "trigger": {"type": "passive"},
				 "target": {"type": "lifepath:self"}, "actions": []}
				"""));
		files.put(LifepathMod.id("bad_trigger"), JsonParser.parseString("""
				{"display_name": "Bad", "trigger": {"type": "telekinesis"},
				 "target": {"type": "lifepath:self"}, "actions": []}
				"""));
		files.put(LifepathMod.id("missing_fields"), JsonParser.parseString("""
				{"display_name": "Missing"}
				"""));

		int loaded = LifepathContent.registerAll("ability", files,
				AbilityDefinition.AbilityFile.CODEC, AbilityDefinition::fromFile,
				LifepathContent.abilities());
		assertEquals(1, loaded);
		assertTrue(LifepathContent.abilities().get(LifepathMod.id("good")) != null);
		assertFalse(LifepathContent.abilities().contains(LifepathMod.id("bad_trigger")));
		assertFalse(LifepathContent.abilities().contains(LifepathMod.id("missing_fields")));
	}
}
