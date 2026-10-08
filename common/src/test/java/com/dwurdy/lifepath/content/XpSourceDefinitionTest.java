package com.dwurdy.lifepath.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class XpSourceDefinitionTest {
	private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("lifepath", "test_src");

	private static XpSourceDefinition parse(String json) {
		var file = XpSourceDefinition.XpSourceFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
		return XpSourceDefinition.fromFile(ID, file);
	}

	private static ActivityEvent event(ResourceLocation type, ResourceLocation source,
			Set<ResourceLocation> tags, ActivityEvent.Cause cause) {
		return new ActivityEvent(null, type, source, tags, cause, 0L, Map.of());
	}

	@Test
	void parsesAllFields() {
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "player_caused_only": true, "base_xp": 0.5,
				 "per_subject": {"minecraft:diamond_ore": 4.0},
				 "required_tags": ["lifepath:ore"],
				 "excluded_subjects": ["minecraft:bedrock"]}
				""");
		assertEquals(ActivityTypes.MINING, def.activity());
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"), def.skill());
		assertTrue(def.playerCausedOnly());
		assertEquals(0.5, def.baseXp());
		assertEquals(4.0, def.perSubject().get(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_ore")));
		assertEquals(Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "ore")), def.requiredTags());
		assertTrue(def.excludedSubjects().contains(ResourceLocation.fromNamespaceAndPath("minecraft", "bedrock")));
	}

	@Test
	void matchesRespectsTypeCauseTagsExclusions() {
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "player_caused_only": true, "base_xp": 1.0,
				 "required_tags": ["lifepath:ore"],
				 "excluded_subjects": ["minecraft:bedrock"]}
				""");
		ResourceLocation stone = ResourceLocation.fromNamespaceAndPath("minecraft", "stone");
		ResourceLocation ore = LifepathMod_id("ore");
		assertTrue(def.matches(event(ActivityTypes.MINING, stone, Set.of(ore), ActivityEvent.Cause.PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.FARMING, stone, Set.of(ore), ActivityEvent.Cause.PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.MINING, stone, Set.of(), ActivityEvent.Cause.PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.MINING, stone, Set.of(ore), ActivityEvent.Cause.NON_PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.MINING, ResourceLocation.fromNamespaceAndPath("minecraft", "bedrock"),
				Set.of(ore), ActivityEvent.Cause.PLAYER)));
	}

	private static ResourceLocation LifepathMod_id(String path) {
		return ResourceLocation.fromNamespaceAndPath("lifepath", path);
	}

	@Test
	void amountForPrefersSubjectOverride() {
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "base_xp": 0.25, "per_subject": {"minecraft:diamond_ore": 4.0}}
				""");
		assertEquals(4.0, def.amountFor(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_ore")));
		assertEquals(0.25, def.amountFor(ResourceLocation.fromNamespaceAndPath("minecraft", "dirt")));
	}

	@Test
	void shippedSourcesParseAndCoverSpecTable() throws Exception {
		// The M2-4 mining table from docs/GAMEDESIGN.md §11 must ship verbatim.
		Path mining = Path.of("src/main/resources/data/lifepath/xp_source/mining.json");
		var miningDef = XpSourceDefinition.fromFile(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"),
				XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(mining))).result().orElseThrow());
		Map<String, Double> expected = Map.of(
				"stone", 0.05, "deepslate", 0.07, "coal_ore", 0.30,
				"iron_ore", 0.60, "gold_ore", 0.80,
				"diamond_ore", 2.00, "ancient_debris", 4.00);
		for (var e : expected.entrySet()) {
			assertEquals(e.getValue(), miningDef.perSubject().get(ResourceLocation.fromNamespaceAndPath("minecraft", e.getKey())),
					e.getKey() + " xp");
		}
		assertTrue(miningDef.perTag().containsKey(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_ores")),
				"family tags must cover deepslate variants");

		// All shipped xp_source files parse.
		try (var files = Files.list(Path.of("src/main/resources/data/lifepath/xp_source"))) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				assertTrue(XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(f))).result().isPresent(),
						f.getFileName() + " must parse");
			}
		}
	}

	@Test
	void shippedSmithingSourceCoversEveryShippedTierTag() throws Exception {
		// Every per_tag key must name a shipped item tag file — a typo'd tag
		// silently falls through to base_xp in-game.
		Path file = Path.of("src/main/resources/data/lifepath/xp_source/smithing.json");
		var def = XpSourceDefinition.fromFile(ResourceLocation.fromNamespaceAndPath("lifepath", "smithing"),
				XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(file))).result().orElseThrow());
		Path tagDir = Path.of("src/main/resources/data/lifepath/tags/item");
		for (ResourceLocation tag : def.perTag().keySet()) {
			if (!tag.getNamespace().equals("lifepath")) {
				continue;
			}
			assertTrue(Files.exists(tagDir.resolve(tag.getPath() + ".json")),
					"xp_source references missing item tag " + tag);
		}
		// The Overgeared material ladder must be present (steel above gold,
		// copper/stone below iron) so forged work doesn't collapse to base_xp.
		for (String tier : new String[]{"smithing_tier_steel", "smithing_tier_copper", "smithing_tier_stone"}) {
			assertTrue(def.perTag().containsKey(LifepathMod_id(tier)),
					"missing per_tag entry for " + tier);
		}
	}

	@Test
	void resolvePrefersSubjectThenTagThenBase() {
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "base_xp": 0.02,
				 "per_subject": {"minecraft:stone": 0.05},
				 "per_tag": {"minecraft:coal_ores": 0.30}}
				""");
		assertEquals(0.05, def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), Set.of()).amount());
		assertTrue(def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"), Set.of()).specific());
		var tagHit = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "deepslate_coal_ore"),
				Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "coal_ores")));
		assertEquals(0.30, tagHit.amount());
		assertTrue(tagHit.specific());
		var unmapped = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "dirt"), Set.of());
		assertEquals(0.02, unmapped.amount());
		assertFalse(unmapped.specific());
	}

	@Test
	void perTagFirstMatchFollowsFileOrder() {
		// Two overlapping tags: the FIRST in JSON order must win. This pins
		// that the decoded map preserves document order end-to-end.
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "base_xp": 0.0,
				 "per_tag": {"minecraft:coal_ores": 0.30, "lifepath:rare_crop": 9.9}}
				""");
		var resolved = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "deepslate_coal_ore"),
				Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "coal_ores"),
						ResourceLocation.fromNamespaceAndPath("lifepath", "rare_crop")));
		assertEquals(0.30, resolved.amount(), "first per_tag entry wins over later matches");
	}

	@Test
	void smithingSourceTierWeightsOrderedByMaterial() throws Exception {
		// §11.3: iron < diamond < netherite — weight by output tier tag.
		Path file = Path.of("src/main/resources/data/lifepath/xp_source/smithing.json");
		var def = XpSourceDefinition.fromFile(ResourceLocation.fromNamespaceAndPath("lifepath", "smithing"),
				XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(file))).result().orElseThrow());
		double iron = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_pickaxe"),
				Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "smithing_tier_iron"))).amount();
		double diamond = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_chestplate"),
				Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "smithing_tier_diamond"))).amount();
		double netherite = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "netherite_chestplate"),
				Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "smithing_tier_netherite"))).amount();
		assertTrue(iron < diamond && diamond < netherite,
				"tier weights must order iron < diamond < netherite");
		assertEquals(0.5, def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "stick"), Set.of()).amount());
	}

	@Test
	void shippedSmithingTagFilesExist() throws Exception {
		// The semantic-tag contract: files must ship so modpack authors can extend them.
		for (String tag : new String[]{"smithing_tier_netherite", "smithing_tier_diamond",
				"smithing_tier_gold", "smithing_tier_iron", "smithing_materials"}) {
			assertTrue(Files.exists(Path.of("src/main/resources/data/lifepath/tags/item/"
					+ tag + ".json")), tag + " tag file must ship");
		}
		assertTrue(Files.exists(Path.of("src/main/resources/data/lifepath/tags/block/smithing_workstations.json")));
	}

	@Test
	void fishingSourceWeightsJunkBelowFishBelowTreasure() throws Exception {
		Path file = Path.of("src/main/resources/data/lifepath/xp_source/fishing.json");
		var def = XpSourceDefinition.fromFile(ResourceLocation.fromNamespaceAndPath("lifepath", "fishing"),
				XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(file))).result().orElseThrow());
		double junk = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "stick"),
				Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "fishing_junk"))).amount();
		double fish = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "cod"),
				Set.of(ResourceLocation.fromNamespaceAndPath("minecraft", "fishes"))).amount();
		double treasure = def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "saddle"),
				Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "fishing_treasure"))).amount();
		assertTrue(junk < fish && fish < treasure,
				"fishing weights must order junk < common fish < treasure");
		assertEquals(0.30, def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "air"), Set.of()).amount());

		// Enchanted treasure fishing_rod overlaps the junk tag — producer adds
		// fishing_treasure for enchanted catches and treasure is FIRST in
		// per_tag order, so it must win.
		assertEquals(2.5, def.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "fishing_rod"),
				Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "fishing_treasure"),
						ResourceLocation.fromNamespaceAndPath("lifepath", "fishing_junk"))).amount());
	}

	@Test
	void miningSourceRequiresMinableTag() throws Exception {
		Path file = Path.of("src/main/resources/data/lifepath/xp_source/mining.json");
		var def = XpSourceDefinition.fromFile(ResourceLocation.fromNamespaceAndPath("lifepath", "mining"),
				XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(file))).result().orElseThrow());
		// Review fix: breaking crops/dirt must not grant mining XP — the
		// source is scoped to the shipped lifepath:minable block tag.
		assertTrue(def.requiredTags().contains(ResourceLocation.fromNamespaceAndPath("lifepath", "minable")));
		// A farming event carrying no minable tag must not match it.
		var harvest = new ActivityEvent(null, ActivityTypes.MINING,
				ResourceLocation.fromNamespaceAndPath("minecraft", "wheat"), Set.of(ResourceLocation.fromNamespaceAndPath("lifepath", "harvested")),
				ActivityEvent.Cause.PLAYER, 0, Map.of());
		assertFalse(def.matches(harvest));
	}
}
