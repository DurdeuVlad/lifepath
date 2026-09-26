package io.github.durdeuvlad.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.ResourceDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.resource.ResourceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M4-6: the shipped TIMELINE §7 synthetic set under
 * {@code data/lifepath_test/ability/} must parse, pass load-time validation,
 * and execute through the real engine paths — data-only abilities, no Java.
 */
class ShippedAbilityDefinitionsTest {
	private static final Path ABILITY_DIR =
			Path.of("src/main/resources/data/lifepath_test/ability");
	private static final Path RESOURCE_DIR =
			Path.of("src/main/resources/data/lifepath_test/resource");
	private static final Identifier FOCUS =
			Identifier.of("lifepath_test", "test_focus");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		LifepathContent.abilities().clear();
		LifepathContent.resources().clear();
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		LifepathContent.abilities().clear();
		LifepathContent.resources().clear();
	}

	private static AbilityDefinition loadShipped(Path file) throws Exception {
		var parsed = AbilityDefinition.AbilityFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString(Files.readString(file))).result()
				.orElseThrow(() -> new AssertionError(file + " failed to parse"));
		Identifier id = Identifier.of("lifepath_test",
				file.getFileName().toString().replace(".json", ""));
		// The production decode path — validates vocabulary types + refs.
		return LifepathContent.decodeAbility(id, parsed);
	}

	private void registerTestSet() throws Exception {
		var resFile = ResourceDefinition.ResourceFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString(
						Files.readString(RESOURCE_DIR.resolve("test_focus.json"))))
				.result().orElseThrow(() -> new AssertionError("test_focus failed"));
		LifepathContent.resources().register(FOCUS,
				ResourceDefinition.fromFile(FOCUS, resFile));
		try (Stream<Path> files = Files.list(ABILITY_DIR)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				var def = loadShipped(f);
				LifepathContent.abilities().register(def.id(), def);
			}
		}
	}

	@Test
	void fiveSyntheticAbilitiesShipAndValidate() throws Exception {
		registerTestSet();
		for (String name : List.of("test_passive_condition", "test_active_cooldown",
				"test_aoe_target", "test_resource_conditioned", "test_state_change")) {
			assertTrue(LifepathContent.abilities()
							.contains(Identifier.of("lifepath_test", name)),
					"missing shipped synthetic ability " + name);
		}
	}

	@Test
	void syntheticSetExecutesThroughEnginePaths() throws Exception {
		registerTestSet();
		Identifier passive = Identifier.of("lifepath_test", "test_passive_condition");
		Identifier active = Identifier.of("lifepath_test", "test_active_cooldown");
		Identifier aoe = Identifier.of("lifepath_test", "test_aoe_target");
		Identifier gated = Identifier.of("lifepath_test", "test_resource_conditioned");
		Identifier stateful = Identifier.of("lifepath_test", "test_state_change");
		for (Identifier id : List.of(passive, active, aoe, gated, stateful)) {
			data.addId(PlayerCharacterData.ListKind.UNLOCKS, id);
		}

		// Passive-with-condition: focus default 50 >= min 10 → executes and
		// drains 1 via its resource_delta action.
		assertEquals(50.0, ResourceService.current(data, FOCUS));
		assertEquals(AbilityEngine.Outcome.EXECUTED, AbilityEngine.evaluate(data, null,
				LifepathContent.abilities().get(passive), 0L, 0.0));
		assertEquals(51.0, ResourceService.current(data, FOCUS));

		// Active-with-cooldown: first fires, second is gated by CooldownService.
		assertEquals(AbilityEngine.Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, active, 0L));
		assertEquals(AbilityEngine.Outcome.ON_COOLDOWN,
				AbilityEngine.tryActivate(data, null, active, 1L));

		// AoE target: headless (self null) → resolver returns empty — the
		// pipeline reaches resolution and reports NO_TARGETS rather than
		// failing validation or crashing.
		assertEquals(AbilityEngine.Outcome.NO_TARGETS,
				AbilityEngine.tryActivate(data, null, aoe, 0L));

		// Resource-conditioned + cost: focus 51 >= 20 → fires, spends 10.
		assertEquals(AbilityEngine.Outcome.EXECUTED,
				AbilityEngine.tryActivate(data, null, gated, 0L));
		assertEquals(41.0, ResourceService.current(data, FOCUS));
		// Below the threshold now fails the condition gate.
		ResourceService.setTo(data, null, FOCUS, 10.0, 0L);
		assertEquals(AbilityEngine.Outcome.CONDITIONS_FAILED,
				AbilityEngine.tryActivate(data, null, gated, 0L));

		// State-changing event ability: a mining event mutates focus via data.
		ResourceService.setTo(data, null, FOCUS, 40.0, 0L);
		AbilityEngine.handleEvent(data, null, Identifier.of("lifepath", "mining"), 0L);
		assertEquals(45.0, ResourceService.current(data, FOCUS));
	}

	@Test
	void validationCollectsEveryProblem() {
		var file = AbilityDefinition.AbilityFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{"display_name": "Broken", "trigger": {"type": "event"},
						 "target": {"type": "lifepath:self"},
						 "actions": [{"type": "lifepath:fake_action",
						              "resource": "lifepath:fake_res",
						              "skill": "lifepath:fake_skill"}]}
						""")).result().orElseThrow();
		var ex = assertThrows(IllegalArgumentException.class,
				() -> LifepathContent.decodeAbility(LifepathMod.id("broken"), file));
		String msg = ex.getMessage();
		assertTrue(msg.contains("fake_action"), msg);
		assertTrue(msg.contains("fake_res"), msg);
		assertTrue(msg.contains("fake_skill"), msg);
		assertTrue(msg.contains("events[]"), msg);
	}

	@Test
	void disabledAbilityRegistersButNeverExecutes() throws Exception {
		registerTestSet();
		var file = AbilityDefinition.AbilityFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("""
						{"display_name": "Off", "enabled": false,
						 "trigger": {"type": "active"},
						 "target": {"type": "lifepath:self"},
						 "actions": [{"type": "lifepath:debug_log"}]}
						""")).result().orElseThrow();
		Identifier off = LifepathMod.id("off");
		LifepathContent.abilities().register(off,
				LifepathContent.decodeAbility(off, file));
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, off);
		assertEquals(AbilityEngine.Outcome.DISABLED,
				AbilityEngine.tryActivate(data, null, off, 0L));
	}
}
