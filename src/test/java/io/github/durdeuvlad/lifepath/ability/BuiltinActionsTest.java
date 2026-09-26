package io.github.durdeuvlad.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.EvalContext;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.TargetContext;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M4-3 action vocabulary: the data-path action ({@code modify_resource}) is
 * fully exercised; entity/position actions must no-op cleanly without the
 * part they need (entity-less or pos-less targets, headless ctx). Live
 * entity effects are verified in-game like every player-dependent surface.
 */
class BuiltinActionsTest {
	private PlayerCharacterData data;
	private EvalContext ctx;
	private TargetContext dataTarget;

	@BeforeEach
	void setUp() {
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		data = PlayerCharacterData.createDefault();
		ctx = new EvalContext(null, data, 0L, LifepathMod.id("test_ability"));
		dataTarget = new TargetContext(null, null, data);
	}

	private static com.google.gson.JsonObject params(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	private static AbilityVocabulary.ActionExecutor action(String name) {
		return AbilityVocabulary.action(LifepathMod.id(name));
	}

	@Test
	void allActionsAndTargetsAreRegistered() {
		for (String name : new String[] {"apply_effect", "remove_effect",
				"modify_attribute", "damage", "heal", "grow_blocks", "freeze_water",
				"highlight_entities", "consume_item", "modify_resource",
				"play_sound", "spawn_particle", "grant_xp", "resource_delta",
				"debug_log"}) {
			assertTrue(action(name) != null, "missing action " + name);
		}
		assertTrue(AbilityVocabulary.target(LifepathMod.id("self")) != null);
		assertTrue(AbilityVocabulary.target(LifepathMod.id("entities_in_radius")) != null);
		assertTrue(AbilityVocabulary.target(LifepathMod.id("blocks_in_radius")) != null);
	}

	@Test
	void modifyResourceSupportsDeltaAndSetTo() {
		Identifier mana = LifepathMod.id("mana");
		data.setResource(mana, new PlayerCharacterData.ResourceState(30, 0, 100));
		action("modify_resource").run(dataTarget, ctx,
				params("{\"resource\": \"lifepath:mana\", \"delta\": 25}"));
		assertEquals(55.0, data.resources().get(mana).current());
		action("modify_resource").run(dataTarget, ctx,
				params("{\"resource\": \"lifepath:mana\", \"set_to\": 7}"));
		assertEquals(7.0, data.resources().get(mana).current());
		// Clamped to the resource's declared max.
		action("modify_resource").run(dataTarget, ctx,
				params("{\"resource\": \"lifepath:mana\", \"delta\": 999}"));
		assertEquals(100.0, data.resources().get(mana).current());
		// Missing resource / missing params → silent no-op.
		action("modify_resource").run(dataTarget, ctx,
				params("{\"resource\": \"lifepath:ghost\", \"delta\": 5}"));
		action("modify_resource").run(dataTarget, ctx, params("{\"delta\": 5}"));
		assertFalse(data.resources().containsKey(LifepathMod.id("ghost")));
	}

	@Test
	void modifyResourceHitsTheTargetNotTheCaster() {
		Identifier mana = LifepathMod.id("mana");
		PlayerCharacterData other = PlayerCharacterData.createDefault();
		data.setResource(mana, new PlayerCharacterData.ResourceState(50, 0, 100));
		other.setResource(mana, new PlayerCharacterData.ResourceState(50, 0, 100));
		action("modify_resource").run(new TargetContext(null, null, other), ctx,
				params("{\"resource\": \"lifepath:mana\", \"delta\": -10}"));
		assertEquals(40.0, other.resources().get(mana).current());
		assertEquals(50.0, data.resources().get(mana).current());
	}

	@Test
	void entityActionsNoOpOnEntitylessTargets() {
		// apply_effect / remove_effect / modify_attribute / damage / heal /
		// consume_item / highlight_entities all need a live entity — a
		// block-resolver or data-path target must not crash them.
		for (String name : new String[] {"apply_effect", "remove_effect",
				"modify_attribute", "damage", "heal", "consume_item",
				"highlight_entities"}) {
			action(name).run(dataTarget, ctx, params("""
					{"effect": "minecraft:speed", "duration": 100, "amplifier": 1,
					 "attribute": "minecraft:generic.movement_speed",
					 "operation": "add_value", "value": 0.1, "duration_ticks": 40,
					 "amount": 5, "source": "magic",
					 "item": "minecraft:diamond", "count": 2,
					 "radius": 8, "visibility": "private"}
					"""));
		}
	}

	@Test
	void positionActionsNoOpWithoutPosOrWorld() {
		for (String name : new String[] {"grow_blocks", "freeze_water",
				"play_sound", "spawn_particle"}) {
			action(name).run(dataTarget, ctx, params("""
					{"growth_rolls": 2, "temporary": true,
					 "sound": "minecraft:block.amethyst_block.chime",
					 "particle": "minecraft:flame", "count": 4}
					"""));
		}
		// pos present but no live player → still no-op (needs ctx.self world).
		TargetContext posOnly = new TargetContext(null, BlockPos.ORIGIN, data);
		for (String name : new String[] {"grow_blocks", "freeze_water",
				"play_sound", "spawn_particle"}) {
			action(name).run(posOnly, ctx, params(
					"{\"sound\": \"minecraft:block.amethyst_block.chime\"}"));
		}
	}

	@Test
	void aoeResolversYieldEmptyWithoutPlayer() {
		assertTrue(AbilityVocabulary.target(LifepathMod.id("entities_in_radius"))
				.resolve(ctx, params("{\"radius\": 8}")).isEmpty());
		assertTrue(AbilityVocabulary.target(LifepathMod.id("blocks_in_radius"))
				.resolve(ctx, params("{\"radius\": 8, \"block\": \"minecraft:stone\"}"))
				.isEmpty());
	}

	@Test
	void aoeAbilityComposesEndToEndThroughTheEngine() {
		// blocks_in_radius → freeze_water: pure data composition, no Java.
		var file = io.github.durdeuvlad.lifepath.content.AbilityDefinition.AbilityFile.CODEC
				.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "Flash Freeze", "trigger": {"type": "active"},
						 "target": {"type": "lifepath:blocks_in_radius",
						            "radius": 5, "block": "minecraft:water"},
						 "actions": [{"type": "lifepath:freeze_water",
						              "temporary": true}],
						 "cooldown": {"seconds": 30}}
						""")).result().orElseThrow();
		Identifier id = LifepathMod.id("flash_freeze");
		LifepathContent.abilities().clear();
		LifepathContent.abilities().register(id,
				io.github.durdeuvlad.lifepath.content.AbilityDefinition.fromFile(id, file));
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, id);
		// Data path: resolver yields no targets headlessly → NO_TARGETS, and the
		// engine handles it as a defined outcome, not a crash.
		assertEquals(AbilityEngine.Outcome.NO_TARGETS,
				AbilityEngine.tryActivate(data, null, id, 1_000L));
	}
}
