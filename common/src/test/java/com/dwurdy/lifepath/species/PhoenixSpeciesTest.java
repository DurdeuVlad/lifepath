package com.dwurdy.lifepath.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityEngine;
import com.dwurdy.lifepath.ability.AbilityVocabulary;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.ConditionDefinition;
import com.dwurdy.lifepath.content.ResourceDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.resource.ResourceService;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M17 Phoenix: the shipped death→Rebirth→True loop and the flame-fuelled
 * flight cost model are pinned at the decode + data-path level —
 * wings grant only while flame is sufficient and the drain only runs
 * while airborne. Live mayfly toggling needs a real player (same as the
 * rest of the player-entity surface).
 */
class PhoenixSpeciesTest {
	private static final Path DATA = Path.of("src/main/resources/data/lifepath");
	private static final ResourceLocation PHOENIX = LifepathMod.id("phoenix");
	private static final ResourceLocation WINGS = LifepathMod.id("phoenix_wings");
	private static final ResourceLocation DRAIN = LifepathMod.id("phoenix_flight_drain");
	private static final ResourceLocation FLAME = LifepathMod.id("flame");
	private static final ResourceLocation REBIRTH = LifepathMod.id("phoenix_rebirth");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() throws Exception {
		data = PlayerCharacterData.createDefault();
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		LifepathContent.species().clear();
		LifepathContent.abilities().clear();
		LifepathContent.resources().clear();
		LifepathContent.conditions().clear();
		// decodeAbility validates `resource` refs against this registry —
		// flame must exist before wings/drain decode.
		LifepathContent.resources().register(FLAME, ResourceDefinition.fromFile(
				FLAME, decode("resource", "flame", ResourceDefinition.ResourceFile.CODEC)));
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		LifepathContent.species().clear();
		LifepathContent.abilities().clear();
		LifepathContent.resources().clear();
		LifepathContent.conditions().clear();
	}

	private static <T> T decode(String domain, String name,
			com.mojang.serialization.Codec<T> codec) throws Exception {
		return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(
						Files.readString(DATA.resolve(domain + "/" + name + ".json"))))
				.result().orElseThrow(() -> new AssertionError(domain + "/" + name
						+ " failed to parse"));
	}

	private static AbilityDefinition decodeAbility(String name) throws Exception {
		return LifepathContent.decodeAbility(LifepathMod.id(name),
				decode("ability", name, AbilityDefinition.AbilityFile.CODEC));
	}

	@Test
	void phoenixSpeciesOwnsFlameAndFlightPair() throws Exception {
		SpeciesDefinition def = SpeciesDefinition.fromFile(PHOENIX,
				decode("species", "phoenix",
						SpeciesDefinition.SpeciesDefinitionFile.CODEC));
		assertTrue(def.resources().contains(FLAME),
				"phoenix must own the flame resource for regen to tick");
		assertTrue(def.passiveAbilities().contains(WINGS));
		assertTrue(def.passiveAbilities().contains(DRAIN));
		assertFalse(def.selection() == SpeciesDefinition.Selection.OPEN,
				"phoenix must stay unlock-gated, never open-pick");
	}

	@Test
	void wingsGateOnFormAndFlame() throws Exception {
		AbilityDefinition def = decodeAbility("phoenix_wings");
		// Decode-time vocabulary validation: every node must be known.
		assertTrue(AbilityVocabulary.unknownNodeTypes(def).isEmpty());
		var all = def.conditions().all();
		assertEquals(2, all.size());
		// The flame gate is the M17-3 "costs something" requirement.
		var gate = all.get(1).raw();
		assertEquals(FLAME, ResourceLocation.parse(gate.get("resource").getAsString()));
		assertTrue(gate.get("value").getAsDouble() > 0);
		// And the grant itself is the refreshed-marker kind.
		assertEquals(LifepathMod.id("grant_flight"), def.actions().get(0).type());
	}

	@Test
	void flightDrainBurnsFlameOnlyWhileAirborne() throws Exception {
		AbilityDefinition def = decodeAbility("phoenix_flight_drain");
		assertTrue(AbilityVocabulary.unknownNodeTypes(def).isEmpty());
		assertEquals(1, def.conditions().all().size());
		assertEquals(LifepathMod.id("is_flying"), def.conditions().all().get(0).type());
		assertEquals(1, def.resourceInteractions().size());
		assertEquals(FLAME, def.resourceInteractions().get(0).resource());
		assertTrue(def.resourceInteractions().get(0).perSecond() < 0.0,
				"flight drain must subtract flame per second");
	}

	@Test
	void rebirthConditionAcquiresOnlyOnPhoenixDeath() throws Exception {
		ConditionDefinition def = ConditionDefinition.fromFile(REBIRTH,
				decode("condition", "phoenix_rebirth",
						ConditionDefinition.ConditionFile.CODEC));
		assertTrue(def.acquisition().stream().anyMatch(rule ->
				"death".equals(rule.type())
						&& rule.species().filter(PHOENIX::equals).isPresent()),
				"rebirth must acquire on phoenix death only");
		assertEquals(2, def.stageCount());
		assertTrue(def.stages().get(0).abilities()
				.contains(LifepathMod.id("phoenix_rebirth_frailty")));
	}

	@Test
	void drainEvalFailsClosedWithoutAPlayer() throws Exception {
		// is_flying reads abilities — with no live player the drain must not
		// burn flame (the cost gate fails safe, not leaky).
		AbilityDefinition drain = decodeAbility("phoenix_flight_drain");
		LifepathContent.abilities().register(drain.id(), drain);
		SpeciesDefinition species = SpeciesDefinition.fromFile(PHOENIX,
				decode("species", "phoenix",
						SpeciesDefinition.SpeciesDefinitionFile.CODEC));
		LifepathContent.species().register(PHOENIX, species);
		data.setSpeciesId(PHOENIX);
		data.setResource(FLAME, new PlayerCharacterData.ResourceState(50.0, 0, 100));

		assertEquals(AbilityEngine.Outcome.CONDITIONS_FAILED,
				AbilityEngine.evaluate(data, null, drain, 1_000L, 1.0));
		assertEquals(50.0, ResourceService.current(data, FLAME));
	}
}
