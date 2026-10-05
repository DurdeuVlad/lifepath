package com.dwurdy.lifepath.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityVocabulary.EvalContext;
import com.dwurdy.lifepath.ability.AbilityVocabulary.TargetContext;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.registry.LifepathContent;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
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

	@BeforeAll
	static void bootMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

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
				"freeze_ticks", "feed", "highlight_entities", "consume_item",
				"modify_resource", "play_sound", "spawn_particle", "grant_xp",
				"resource_delta", "debug_log"}) {
			assertTrue(action(name) != null, "missing action " + name);
		}
		assertTrue(AbilityVocabulary.target(LifepathMod.id("self")) != null);
		assertTrue(AbilityVocabulary.target(LifepathMod.id("victim")) != null);
		assertTrue(AbilityVocabulary.target(LifepathMod.id("entities_in_radius")) != null);
		assertTrue(AbilityVocabulary.target(LifepathMod.id("blocks_in_radius")) != null);
	}

	@Test
	void modifyResourceSupportsDeltaAndSetTo() {
		ResourceLocation mana = LifepathMod.id("mana");
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
		ResourceLocation mana = LifepathMod.id("mana");
		PlayerCharacterData other = PlayerCharacterData.createDefault();
		data.setResource(mana, new PlayerCharacterData.ResourceState(50, 0, 100));
		other.setResource(mana, new PlayerCharacterData.ResourceState(50, 0, 100));
		action("modify_resource").run(new TargetContext(null, null, other), ctx,
				params("{\"resource\": \"lifepath:mana\", \"delta\": -10}"));
		assertEquals(40.0, other.resources().get(mana).current());
		assertEquals(50.0, data.resources().get(mana).current());
	}

	@Test
	void resourceActionsNoOpOnModellessTargets() {
		// Non-player / block targets carry no character model (per-target
		// semantics) — resource/XP actions must no-op, not NPE or multiply
		// against the caster's model.
		TargetContext blockTarget = new TargetContext(null, BlockPos.ZERO, null);
		action("modify_resource").run(blockTarget, ctx,
				params("{\"resource\": \"lifepath:mana\", \"delta\": 25}"));
		action("resource_delta").run(blockTarget, ctx,
				params("{\"resource\": \"lifepath:mana\", \"amount\": 25}"));
		action("grant_xp").run(blockTarget, ctx,
				params("{\"skill\": \"lifepath:mining\", \"amount\": 5}"));
		assertTrue(data.resources().isEmpty(), "caster model must stay untouched");
	}

	@Test
	void entityActionsNoOpOnEntitylessTargets() {
		// apply_effect / remove_effect / modify_attribute / damage / heal /
		// consume_item / highlight_entities / freeze_ticks / feed all need a
		// live entity — a block-resolver or data-path target must not crash them.
		for (String name : new String[] {"apply_effect", "remove_effect",
				"modify_attribute", "damage", "heal", "consume_item",
				"highlight_entities", "freeze_ticks", "feed"}) {
			action(name).run(dataTarget, ctx, params("""
					{"effect": "minecraft:speed", "duration": 100, "amplifier": 1,
					 "attribute": "minecraft:generic.movement_speed",
					 "operation": "add_value", "value": 0.1, "duration_ticks": 40,
					 "amount": 5, "source": "magic",
					 "item": "minecraft:diamond", "count": 2,
					 "radius": 8, "visibility": "private", "ticks": 200,
					 "nutrition": 4, "saturation_modifier": 0.3}
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
		TargetContext posOnly = new TargetContext(null, BlockPos.ZERO, data);
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
	void victimResolverYieldsEmptyOffTheDealtPath() {
		// No damage context at all → empty; a damage_taken-style context
		// (victim unset) → empty too.
		assertTrue(AbilityVocabulary.target(LifepathMod.id("victim"))
				.resolve(ctx, params("{}")).isEmpty());
		var takenCtx = new EvalContext(null, data, 0L, null,
				new AbilityVocabulary.DamageInfo(null, null, 4f));
		assertTrue(AbilityVocabulary.target(LifepathMod.id("victim"))
				.resolve(takenCtx, params("{}")).isEmpty());
	}

	@Test
	void consumeMatchingReachesEverySlotInventoryContainsCounts() {
		// Regression: inventory_contains counts all getContainerSize() slots
		// (main + armor + offhand). consume_item scoped to main-only let an
		// offhand iron nugget satisfy Devour Iron's gate while never being
		// consumed — free feed forever.
		Inventory inv = new Inventory(null);
		inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.IRON_NUGGET, 2));
		BuiltinActions.consumeMatching(inv, s -> s.is(Items.IRON_NUGGET), 1);
		assertEquals(1, inv.getItem(Inventory.SLOT_OFFHAND).getCount());
		BuiltinActions.consumeMatching(inv, s -> s.is(Items.IRON_NUGGET), 5);
		assertTrue(inv.getItem(Inventory.SLOT_OFFHAND).isEmpty());
	}

	@Test
	void consumeMatchingConsumesAcrossCompartmentsUntilCountIsMet() {
		// Split stacks: 1 nugget in main, 1 in offhand — count 2 drains both.
		// Worn gear is consumable when matched — inventory_contains already
		// counts it as "carried", and the matcher is the author's scope.
		Inventory inv = new Inventory(null);
		inv.setItem(0, new ItemStack(Items.IRON_NUGGET, 1));
		inv.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.IRON_NUGGET, 1));
		inv.armor.set(0, new ItemStack(Items.IRON_HELMET));
		BuiltinActions.consumeMatching(inv, s -> s.is(Items.IRON_NUGGET), 2);
		assertTrue(inv.getItem(0).isEmpty());
		assertTrue(inv.getItem(Inventory.SLOT_OFFHAND).isEmpty());
		BuiltinActions.consumeMatching(inv, s -> s.is(Items.IRON_HELMET), 1);
		assertTrue(inv.armor.get(0).isEmpty());
		// Non-matching stacks are untouched.
		inv.setItem(1, new ItemStack(Items.DIRT, 3));
		BuiltinActions.consumeMatching(inv, s -> s.is(Items.IRON_NUGGET), 1);
		assertEquals(3, inv.getItem(1).getCount());
	}

	@Test
	void nonFiniteParamsFallBackToDefaults() {
		// Gson's lenient parser admits bare NaN/Infinity literals — a NaN
		// saturation_modifier would poison FoodData (NaN saturation never
		// drains). Non-finite values must fall back to the default.
		JsonObject p = new JsonObject();
		p.add("x", new JsonPrimitive(Double.NaN));
		assertEquals(1.5, AbilityVocabulary.num(p, "x", 1.5));
		p.add("x", new JsonPrimitive(Double.POSITIVE_INFINITY));
		assertEquals(1.5, AbilityVocabulary.num(p, "x", 1.5));
		p.add("x", new JsonPrimitive(Double.NEGATIVE_INFINITY));
		assertEquals(1.5, AbilityVocabulary.num(p, "x", 1.5));
		p.add("x", new JsonPrimitive(5.25));
		assertEquals(5.25, AbilityVocabulary.num(p, "x", 1.5));
	}

	@Test
	void aoeAbilityComposesEndToEndThroughTheEngine() {
		// blocks_in_radius → freeze_water: pure data composition, no Java.
		var file = com.dwurdy.lifepath.content.AbilityDefinition.AbilityFile.CODEC
				.parse(com.mojang.serialization.JsonOps.INSTANCE, JsonParser.parseString("""
						{"display_name": "Flash Freeze", "trigger": {"type": "active"},
						 "target": {"type": "lifepath:blocks_in_radius",
						            "radius": 5, "block": "minecraft:water"},
						 "actions": [{"type": "lifepath:freeze_water",
						              "temporary": true}],
						 "cooldown": {"seconds": 30}}
						""")).result().orElseThrow();
		ResourceLocation id = LifepathMod.id("flash_freeze");
		LifepathContent.abilities().clear();
		LifepathContent.abilities().register(id,
				com.dwurdy.lifepath.content.AbilityDefinition.fromFile(id, file));
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, id);
		// Data path: resolver yields no targets headlessly → NO_TARGETS, and the
		// engine handles it as a defined outcome, not a crash.
		assertEquals(AbilityEngine.Outcome.NO_TARGETS,
				AbilityEngine.tryActivate(data, null, id, 1_000L));
	}
}
