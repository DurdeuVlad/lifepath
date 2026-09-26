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
	void shippedExampleParsesAndIsInert() throws Exception {
		Path file = Path.of("src/main/resources/data/lifepath/xp_source/example_mining.json");
		assertTrue(Files.exists(file), "example xp_source must ship");
		var def = XpSourceDefinition.fromFile(Identifier.of("lifepath", "example_mining"),
				XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
						JsonParser.parseString(Files.readString(file))).result().orElseThrow());
		assertEquals(ActivityTypes.MINING, def.activity());
		// Inert: its only subject is a nonexistent lifepath id — it can never award.
		assertTrue(def.matches(event(ActivityTypes.MINING, Identifier.of("lifepath", "example_stone"),
				Set.of(), ActivityEvent.Cause.PLAYER)) == def.amountFor(
						Identifier.of("lifepath", "example_stone")) > 0);
		assertEquals(0.0, def.amountFor(Identifier.of("minecraft", "stone")));
	}
}
