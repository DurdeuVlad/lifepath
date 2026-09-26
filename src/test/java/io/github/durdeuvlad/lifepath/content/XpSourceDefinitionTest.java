package io.github.durdeuvlad.lifepath.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.ActivityTypes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class XpSourceDefinitionTest {
	private static final Identifier ID = Identifier.of("lifepath", "test_src");

	private static XpSourceDefinition parse(String json) {
		var file = XpSourceDefinition.XpSourceFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
		return XpSourceDefinition.fromFile(ID, file);
	}

	private static ActivityEvent event(Identifier type, Identifier source,
			Set<Identifier> tags, ActivityEvent.Cause cause) {
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
		assertEquals(Identifier.of("lifepath", "mining"), def.skill());
		assertTrue(def.playerCausedOnly());
		assertEquals(0.5, def.baseXp());
		assertEquals(4.0, def.perSubject().get(Identifier.of("minecraft", "diamond_ore")));
		assertEquals(Set.of(Identifier.of("lifepath", "ore")), def.requiredTags());
		assertTrue(def.excludedSubjects().contains(Identifier.of("minecraft", "bedrock")));
	}

	@Test
	void matchesRespectsTypeCauseTagsExclusions() {
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "player_caused_only": true, "base_xp": 1.0,
				 "required_tags": ["lifepath:ore"],
				 "excluded_subjects": ["minecraft:bedrock"]}
				""");
		Identifier stone = Identifier.of("minecraft", "stone");
		Identifier ore = LifepathMod_id("ore");
		assertTrue(def.matches(event(ActivityTypes.MINING, stone, Set.of(ore), ActivityEvent.Cause.PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.FARMING, stone, Set.of(ore), ActivityEvent.Cause.PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.MINING, stone, Set.of(), ActivityEvent.Cause.PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.MINING, stone, Set.of(ore), ActivityEvent.Cause.NON_PLAYER)));
		assertFalse(def.matches(event(ActivityTypes.MINING, Identifier.of("minecraft", "bedrock"),
				Set.of(ore), ActivityEvent.Cause.PLAYER)));
	}

	private static Identifier LifepathMod_id(String path) {
		return Identifier.of("lifepath", path);
	}

	@Test
	void amountForPrefersSubjectOverride() {
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "base_xp": 0.25, "per_subject": {"minecraft:diamond_ore": 4.0}}
				""");
		assertEquals(4.0, def.amountFor(Identifier.of("minecraft", "diamond_ore")));
		assertEquals(0.25, def.amountFor(Identifier.of("minecraft", "dirt")));
	}

	@Test
	void shippedSourcesParseAndCoverSpecTable() throws Exception {
		// The M2-4 mining table from docs/GAMEDESIGN.md §11 must ship verbatim.
		Path mining = Path.of("src/main/resources/data/lifepath/xp_source/mining.json");
		var miningDef = XpSourceDefinition.fromFile(Identifier.of("lifepath", "mining"),
				XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(mining))).result().orElseThrow());
		Map<String, Double> expected = Map.of(
				"stone", 0.05, "deepslate", 0.07, "coal_ore", 0.30,
				"iron_ore", 0.60, "gold_ore", 0.80,
				"diamond_ore", 2.00, "ancient_debris", 4.00);
		for (var e : expected.entrySet()) {
			assertEquals(e.getValue(), miningDef.perSubject().get(Identifier.of("minecraft", e.getKey())),
					e.getKey() + " xp");
		}
		assertTrue(miningDef.perTag().containsKey(Identifier.of("minecraft", "diamond_ores")),
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
	void resolvePrefersSubjectThenTagThenBase() {
		var def = parse("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "base_xp": 0.02,
				 "per_subject": {"minecraft:stone": 0.05},
				 "per_tag": {"minecraft:coal_ores": 0.30}}
				""");
		assertEquals(0.05, def.resolve(Identifier.of("minecraft", "stone"), Set.of()).amount());
		assertTrue(def.resolve(Identifier.of("minecraft", "stone"), Set.of()).specific());
		// Tag hit: deepslate coal ore carries minecraft:coal_ores.
		var tagHit = def.resolve(Identifier.of("minecraft", "deepslate_coal_ore"),
				Set.of(Identifier.of("minecraft", "coal_ores")));
		assertEquals(0.30, tagHit.amount());
		assertTrue(tagHit.specific());
		// Unmapped: base_xp, non-specific.
		var unmapped = def.resolve(Identifier.of("minecraft", "dirt"), Set.of());
		assertEquals(0.02, unmapped.amount());
		assertFalse(unmapped.specific());
	}
}
