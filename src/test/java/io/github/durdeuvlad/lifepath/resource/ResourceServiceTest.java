package io.github.durdeuvlad.lifepath.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.ResourceDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M4-5 resource framework: codec validation, lazy defaults, def-bounds
 * clamping, band entry/exit transitions (once-on-entry actions, sustained
 * effect channel), regen sweep, oscillation guard — all data-path (player
 * null); status-effect application is entity-path, verified in-game.
 */
class ResourceServiceTest {
	private static final Identifier TEMP = LifepathMod.id("temperature");
	private static final Identifier CAPTURE = LifepathMod.id("test_capture");

	private PlayerCharacterData data;
	private final List<ActivityEvent> events = new ArrayList<>();
	private final AtomicInteger captureCount = new AtomicInteger();

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		events.clear();
		captureCount.set(0);
		ActivityDispatcher.resetForTests();
		ActivityDispatcher.registerAny(events::add);
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		AbilityVocabulary.registerAction(CAPTURE,
				(target, ctx, params) -> captureCount.incrementAndGet());
		LifepathContent.resources().clear();
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		LifepathContent.resources().clear();
	}

	private static ResourceDefinition parse(String json) {
		var file = ResourceDefinition.ResourceFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
				.result().orElseThrow();
		return ResourceDefinition.fromFile(TEMP, file);
	}

	private void register(ResourceDefinition def) {
		LifepathContent.resources().clear();
		LifepathContent.resources().register(TEMP, def);
	}

	private long bandEvents(Identifier type) {
		return events.stream().filter(e -> e.type().equals(type)).count();
	}

	@Test
	void fileCodecParsesBandsAndRejectsMalformed() {
		ResourceDefinition def = parse("""
				{"min": 0, "max": 100, "default": 50, "regen_per_second": -0.5,
				 "bands": [{"range": [86, 99],
				    "effects": [{"effect": "minecraft:weakness",
				                 "duration_ticks": 60, "amplifier": 0}],
				    "actions": [{"type": "lifepath:test_capture"}]}]}
				""");
		assertEquals(-0.5, def.regenPerSecond());
		assertEquals(1, def.bands().size());
		assertEquals(86.0, def.bands().get(0).lo());
		assertEquals(1, def.bands().get(0).effects().size());
		assertEquals(1, def.bands().get(0).actions().size());

		assertThrows(Exception.class,
				() -> parse("{\"min\": 100, \"max\": 0}"));                    // min >= max
		assertThrows(Exception.class,
				() -> parse("{\"min\": 0, \"max\": 100, \"default\": 500}"));  // default out of range
		assertThrows(Exception.class, () -> parse(
				"{\"min\": 0, \"max\": 100, \"bands\": [{\"range\": [80, 60]}]}")); // reversed
		assertThrows(Exception.class, () -> parse("""
				{"min": 0, "max": 100,
				 "bands": [{"range": [10, 50]}, {"range": [40, 90]}]}
				"""));                                                        // overlapping
	}

	@Test
	void unmaterializedResourceReadsAsDefinitionDefault() {
		register(parse("{\"min\": 0, \"max\": 100, \"default\": 42}"));
		assertTrue(data.resources().isEmpty());
		assertEquals(42.0, ResourceService.current(data, TEMP));
		assertTrue(data.resources().isEmpty(), "reads must not materialize");
		assertEquals(0.0, ResourceService.current(data, LifepathMod.id("ghost")));
	}

	@Test
	void modifyClampsToDefinitionBoundsAndMaterializes() {
		register(parse("{\"min\": 0, \"max\": 100, \"default\": 50}"));
		assertEquals(130.0 - 30, // delta +80 from default 50 → clamps to 100
				ResourceService.modify(data, null, TEMP, 80.0, 0L));
		assertEquals(100.0, ResourceService.current(data, TEMP));
		// delta path on a materialized state
		assertEquals(0.0, ResourceService.modify(data, null, TEMP, -500.0, 0L));
		assertEquals(25.0, ResourceService.setTo(data, null, TEMP, 25.0, 0L));
		// Unknown + unmaterialized → no-op, no ghost state.
		ResourceService.modify(data, null, LifepathMod.id("ghost"), 5.0, 0L);
		assertFalse(data.resources().containsKey(LifepathMod.id("ghost")));
	}

	@Test
	void bandEntryFiresActionsOnceAndPublishesEvents() {
		register(parse("""
				{"min": 0, "max": 100, "default": 50,
				 "bands": [{"range": [80, 100],
				    "actions": [{"type": "lifepath:test_capture"}]}]}
				"""));
		// Crossing into the band fires its actions + a BAND_ENTER event.
		ResourceService.modify(data, null, TEMP, 40.0, 1000L);
		assertEquals(90.0, ResourceService.current(data, TEMP));
		assertEquals(1, captureCount.get());
		assertEquals(1, bandEvents(ResourceService.BAND_ENTER));

		// Moving inside the same band does NOT refire.
		ResourceService.modify(data, null, TEMP, 5.0, 2000L);
		assertEquals(1, captureCount.get());
		assertEquals(1, bandEvents(ResourceService.BAND_ENTER));

		// Leaving fires BAND_EXIT once; re-entering fires again.
		ResourceService.modify(data, null, TEMP, -60.0, 3000L);
		assertEquals(1, bandEvents(ResourceService.BAND_EXIT));
		assertEquals(1, captureCount.get());
		ResourceService.modify(data, null, TEMP, 60.0, 4000L);
		assertEquals(2, captureCount.get());
		assertEquals(2, bandEvents(ResourceService.BAND_ENTER));
	}

	@Test
	void regenSweepAppliesRatePerIntervalAndCrossesBands() {
		register(parse("""
				{"min": 0, "max": 100, "default": 40, "regen_per_second": -10.0,
				 "bands": [{"range": [0, 20],
				    "actions": [{"type": "lifepath:test_capture"}]}]}
				"""));
		// Unowned resources don't tick — materialize first (or declare via species).
		ResourceService.setTo(data, null, TEMP, 40.0, 0L);
		ResourceService.tickPlayer(data, null, 0L, 1.0); // 1s sweep → −10
		assertEquals(30.0, ResourceService.current(data, TEMP));
		ResourceService.tickPlayer(data, null, 0L, 1.0);
		assertEquals(20.0, ResourceService.current(data, TEMP));
		assertEquals(1, captureCount.get(), "regen crossed into the low band");
	}

	@Test
	void speciesDeclaredResourcesTickEvenUnmaterialized() {
		register(parse("{\"min\": 0, \"max\": 100, \"default\": 50,"
				+ " \"regen_per_second\": 2.0}"));
		Identifier speciesId = LifepathMod.id("frost");
		LifepathContent.species().clear();
		LifepathContent.species().register(speciesId, new SpeciesDefinition(speciesId,
				"Frost", SpeciesDefinition.Visibility.NORMAL,
				SpeciesDefinition.Selection.OPEN, List.of(), List.of(), Map.of(),
				List.of(TEMP), Optional.empty(), Optional.empty()));
		data.setSpeciesId(speciesId);
		ResourceService.tickPlayer(data, null, 0L, 1.0);
		assertEquals(52.0, ResourceService.current(data, TEMP));
		LifepathContent.species().clear();
	}

	@Test
	void bandOscillationIsDepthCapped() {
		// Band A's entry pushes into B; B's entry pushes back into A — a real
		// infinite ping-pong that only the depth cap terminates.
		AbilityVocabulary.registerAction(LifepathMod.id("push_high"), (target, ctx, params) -> {
			captureCount.incrementAndGet();
			ResourceService.setTo(ctx.data(), null, TEMP, 70.0, ctx.now());
		});
		AbilityVocabulary.registerAction(LifepathMod.id("push_low"), (target, ctx, params) -> {
			captureCount.incrementAndGet();
			ResourceService.setTo(ctx.data(), null, TEMP, 40.0, ctx.now());
		});
		register(parse("""
				{"min": 0, "max": 100, "default": 10,
				 "bands": [{"range": [30, 50],
				             "actions": [{"type": "lifepath:push_high"}]},
				           {"range": [60, 90],
				             "actions": [{"type": "lifepath:push_low"}]}]}
				"""));
		ResourceService.setTo(data, null, TEMP, 40.0, 0L); // enters A → cascades
		assertTrue(captureCount.get() > 0, "entry actions did fire");
		assertTrue(captureCount.get() <= 2 * 4 + 1,
				"depth cap must bound the cascade, got " + captureCount.get());
	}

	@Test
	void thresholdAndHasResourceSeeDefinitionDefaults() {
		register(parse("{\"min\": 0, \"max\": 100, \"default\": 50}"));
		AbilityVocabulary.EvalContext ctx = new AbilityVocabulary.EvalContext(
				null, data, 0L, null);
		var threshold = AbilityVocabulary.condition(LifepathMod.id("resource_threshold"));
		assertTrue(threshold.test(ctx, JsonParser.parseString(
				"{\"resource\": \"lifepath:temperature\", \"op\": \">=\", \"value\": 40}")
				.getAsJsonObject()));
		assertFalse(threshold.test(ctx, JsonParser.parseString(
				"{\"resource\": \"lifepath:temperature\", \"op\": \">=\", \"value\": 60}")
				.getAsJsonObject()));
		var has = AbilityVocabulary.condition(LifepathMod.id("has_resource"));
		assertTrue(has.test(ctx, JsonParser.parseString(
				"{\"resource\": \"lifepath:temperature\", \"min\": 50}").getAsJsonObject()));
	}

	@Test
	void costGatePaysFromDefinitionDefault() {
		register(parse("{\"min\": 0, \"max\": 100, \"default\": 50}"));
		// Ability costs can be paid by an unmaterialized resource at default.
		var file = AbilityDefinition.AbilityFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "Spend", "trigger": {"type": "active"},
						 "target": {"type": "lifepath:self"},
						 "actions": [{"type": "lifepath:test_capture"}],
						 "cost": {"resource": "lifepath:temperature", "amount": 30}}
						""")).result().orElseThrow();
		Identifier abilityId = LifepathMod.id("spender");
		LifepathContent.abilities().clear();
		LifepathContent.abilities().register(abilityId,
				AbilityDefinition.fromFile(abilityId, file));
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, abilityId);
		assertEquals(io.github.durdeuvlad.lifepath.ability.AbilityEngine.Outcome.EXECUTED,
				io.github.durdeuvlad.lifepath.ability.AbilityEngine
						.tryActivate(data, null, abilityId, 0L));
		assertEquals(20.0, ResourceService.current(data, TEMP));
	}
}
