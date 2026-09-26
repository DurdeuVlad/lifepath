package io.github.durdeuvlad.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.DamageInfo;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.EvalContext;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M5-2 incoming-damage mechanic: the {@code damage_taken} trigger kind +
 * DamageInfo context + the shipped Sylvian files. Data-path coverage —
 * attacker/type conditions need live entities; {@code damage_amount} and the
 * multiplier pipeline are exercised end-to-end.
 */
class DamageTakenTest {
	private static final Path SYLVIAN_DIR =
			Path.of("src/main/resources/data/lifepath/ability");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		LifepathContent.abilities().clear();
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		LifepathContent.abilities().clear();
	}

	private AbilityDefinition ability(String id, String json) {
		var file = AbilityDefinition.AbilityFile.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString(json)).result().orElseThrow();
		Identifier iid = LifepathMod.id(id);
		return LifepathContent.decodeAbility(iid, file);
	}

	@Test
	void damageTakenMultipliesPerPassingCondition() {
		registerAll(List.of(
				ability("fragile", """
						{"display_name": "Fragile",
						 "trigger": {"type": "damage_taken", "multiplier": 1.5},
						 "conditions": {"all": [{"type": "lifepath:damage_amount",
						                         "op": ">=", "value": 10}]},
						 "target": {"type": "lifepath:self"}}
						"""),
				ability("heavy", """
						{"display_name": "Heavy",
						 "trigger": {"type": "damage_taken", "multiplier": 2.0},
						 "conditions": {"all": [{"type": "lifepath:damage_amount",
						                         "op": ">=", "value": 10}]},
						 "target": {"type": "lifepath:self"}}
						""")));
		for (Identifier id : List.of(LifepathMod.id("fragile"), LifepathMod.id("heavy"))) {
			data.addId(PlayerCharacterData.ListKind.UNLOCKS, id);
		}
		// Both pass at amount 10 → multiplicative 1.5 × 2.0 = 3.0.
		assertEquals(30.0f, AbilityEngine.modifyIncomingDamage(data, null,
				new DamageInfo(null, null, 10f), 10f));
		// Below threshold → untouched.
		assertEquals(5.0f, AbilityEngine.modifyIncomingDamage(data, null,
				new DamageInfo(null, null, 5f), 5f));
		// No damage context at all → conditions fail closed.
		assertEquals(10.0f, AbilityEngine.modifyIncomingDamage(data, null,
				null, 10f));
	}

	@Test
	void nonDamageTakenDefsAreIgnored() {
		registerAll(List.of(ability("normal", """
				{"display_name": "Normal", "trigger": {"type": "active"},
				 "target": {"type": "lifepath:self"},
				 "actions": [{"type": "lifepath:debug_log"}]}
				""")));
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, LifepathMod.id("normal"));
		assertEquals(4.0f, AbilityEngine.modifyIncomingDamage(data, null,
				new DamageInfo(null, null, 4f), 4f));
	}

	@Test
	void damageConditionsFailClosedWithoutContext() {
		EvalContext bare = new EvalContext(null, data, 0L, null);
		for (String type : List.of("attacker_entity", "damage_type", "damage_amount")) {
			var cond = AbilityVocabulary.condition(LifepathMod.id(type));
			assertFalse(cond.test(bare, JsonParser.parseString(
					"{\"entity\": \"#lifepath:undead\", \"type\": \"minecraft:generic\", "
							+ "\"op\": \">=\", \"value\": 1}").getAsJsonObject()),
					type + " must fail closed with no damage context");
		}
	}

	private void registerAll(List<AbilityDefinition> defs) {
		for (var d : defs) {
			LifepathContent.abilities().register(d.id(), d);
		}
	}

	@Test
	void sylvianFilesAllValidateAndReferenceRealContent() throws Exception {
		// Every shipped sylvian ability parses + validates; the species file
		// references exactly them.
		try (Stream<Path> files = Files.list(SYLVIAN_DIR)) {
			for (Path f : files.filter(p -> p.getFileName().toString()
					.startsWith("sylvian_")).toList()) {
				var parsed = AbilityDefinition.AbilityFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(f))).result()
						.orElseThrow(() -> new AssertionError(f + " failed to parse"));
				Identifier id = Identifier.of("lifepath",
						f.getFileName().toString().replace(".json", ""));
				var def = LifepathContent.decodeAbility(id, parsed);
				assertTrue(AbilityVocabulary.unknownNodeTypes(def).isEmpty(),
						id + " uses unknown spec nodes");
			}
		}
		var speciesFile = io.github.durdeuvlad.lifepath.content.SpeciesDefinition
				.SpeciesDefinitionFile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
						Files.readString(Path.of(
								"src/main/resources/data/lifepath/species/sylvian.json"))))
				.result().orElseThrow(() -> new AssertionError("sylvian species failed"));
		// The seven expected refs — six passives + the active.
		assertEquals(6, speciesFile.passiveAbilities().size());
		assertEquals(List.of(LifepathMod.id("sylvian_verdant_bloom")),
				speciesFile.activeAbilities());
	}
}
