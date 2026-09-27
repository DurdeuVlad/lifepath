package io.github.durdeuvlad.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.content.XpSourceDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.ActivityTypes;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the M2-3 pipeline end-to-end: a synthetic ActivityEvent flows
 * dispatcher → router → data-defined xp_source → SkillXpService → stored
 * progress — with zero vanilla/gameplay imports in the skill layer.
 */
class XpSourceRouterTest {
	private static final ResourceLocation MINING_SKILL = ResourceLocation.fromNamespaceAndPath("lifepath", "mining");
	private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("minecraft", "stone");

	@BeforeEach
	void setUp() {
		LifepathContent.skills().clear();
		LifepathContent.levelCurves().clear();
		LifepathContent.xpSources().clear();
		ActivityDispatcher.resetForTests();
		XpSourceRouter.resetForTests();
		SkillXpService.resetForTests();
		SkillService.resetForTests();
	}

	private static void registerSkill() {
		SkillDefinition.SkillDefinitionFile file = SkillDefinition.SkillDefinitionFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(
						"{\"display_name\": \"Mining\", \"category\": \"gathering\", \"max_level\": 100}"))
				.result().orElseThrow();
		LifepathContent.skills().register(MINING_SKILL, SkillDefinition.fromFile(MINING_SKILL, file));
	}

	private static void registerSource(String json, ResourceLocation id) {
		XpSourceDefinition.XpSourceFile file = XpSourceDefinition.XpSourceFile.CODEC
				.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
		LifepathContent.xpSources().register(id, XpSourceDefinition.fromFile(id, file));
	}

	@Test
	void syntheticEventProducesAwardEndToEnd() {
		registerSkill();
		registerSource("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "per_subject": {"minecraft:stone": 0.05}}
				""", ResourceLocation.fromNamespaceAndPath("lifepath", "test_mining"));

		PlayerCharacterData data = PlayerCharacterData.createDefault();
		// Test sink: no ServerPlayer needed — award lands on the model.
		XpSourceRouter.setSinkForTests((player, skillId, amount, src) ->
				SkillXpService.awardXpCore(data, skillId, amount, src));
		XpSourceRouter.init();

		ActivityDispatcher.publish(ActivityEvent.of(ActivityTypes.MINING, STONE));
		assertEquals(0.05, data.skill(MINING_SKILL).xp(), 1e-9);
	}

	@Test
	void matchingRulesRespected() {
		registerSkill();
		registerSource("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "player_caused_only": true,
				 "required_tags": ["lifepath:ore"],
				 "excluded_subjects": ["minecraft:bedrock"],
				 "per_subject": {"minecraft:stone": 2.0}}
				""", ResourceLocation.fromNamespaceAndPath("lifepath", "strict_mining"));

		// Wrong activity type.
		assertTrue(XpSourceRouter.plan(ActivityEvent.of(ActivityTypes.FARMING, STONE)).isEmpty());
		// Missing required tag.
		assertTrue(XpSourceRouter.plan(ActivityEvent.of(ActivityTypes.MINING, STONE)).isEmpty());
		// Excluded subject.
		assertTrue(XpSourceRouter.plan(new ActivityEvent(null, ActivityTypes.MINING,
				ResourceLocation.fromNamespaceAndPath("minecraft", "bedrock"), Set.of(LifepathMod.id("ore")),
				ActivityEvent.Cause.PLAYER, 0L, Map.of())).isEmpty());
		// Non-player cause when player_caused_only.
		assertTrue(XpSourceRouter.plan(new ActivityEvent(null, ActivityTypes.MINING,
				STONE, Set.of(LifepathMod.id("ore")),
				ActivityEvent.Cause.NON_PLAYER, 0L, Map.of())).isEmpty());
		// The full match yields the per-subject amount.
		var awards = XpSourceRouter.plan(new ActivityEvent(null, ActivityTypes.MINING,
				STONE, Set.of(LifepathMod.id("ore")),
				ActivityEvent.Cause.PLAYER, 0L, Map.of()));
		assertEquals(1, awards.size());
		assertEquals(MINING_SKILL, awards.get(0).skillId());
		assertEquals(2.0, awards.get(0).amount());
	}

	@Test
	void unlistedSubjectFallsBackToBaseXp() {
		registerSkill();
		registerSource("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "base_xp": 0.5, "per_subject": {"minecraft:diamond_ore": 4.0}}
				""", ResourceLocation.fromNamespaceAndPath("lifepath", "mining_base"));

		var stoneAward = XpSourceRouter.plan(ActivityEvent.of(ActivityTypes.MINING, STONE));
		assertEquals(0.5, stoneAward.get(0).amount());
		var oreAward = XpSourceRouter.plan(ActivityEvent.of(ActivityTypes.MINING,
				ResourceLocation.fromNamespaceAndPath("minecraft", "diamond_ore")));
		assertEquals(4.0, oreAward.get(0).amount());
	}

	@Test
	void unmappedGateBlocksBaseXpWhenDisabled() throws Exception {
		registerSkill();
		registerSource("""
				{"activity": "lifepath:mining", "skill": "lifepath:mining",
				 "base_xp": 0.5, "per_subject": {"minecraft:stone": 2.0}}
				""", ResourceLocation.fromNamespaceAndPath("lifepath", "gated"));

		var dir = java.nio.file.Files.createTempDirectory("unmapped_cfg");
		java.nio.file.Files.writeString(dir.resolve("skills.toml"),
				"unmapped_sources_award_xp = false\n");
		io.github.durdeuvlad.lifepath.config.LifepathConfig.resetForTests();
		io.github.durdeuvlad.lifepath.config.LifepathConfig.define(LifepathMod.id("skills"),
				io.github.durdeuvlad.lifepath.config.ConfigSpec.builder()
						.define("unmapped_sources_award_xp", true, "test")
						.build());
		io.github.durdeuvlad.lifepath.config.LifepathConfig.loadAll(dir);
		try {
			// Mapped subject still awards; unmapped subject is gated off.
			assertEquals(2.0, XpSourceRouter.plan(
					ActivityEvent.of(ActivityTypes.MINING, STONE)).get(0).amount());
			assertTrue(XpSourceRouter.plan(ActivityEvent.of(ActivityTypes.MINING,
					ResourceLocation.fromNamespaceAndPath("minecraft", "dirt"))).isEmpty());
		} finally {
			io.github.durdeuvlad.lifepath.config.LifepathConfig.resetForTests();
		}
	}

	@Test
	void noEventMeansNoXp() {
		// "Ordinary non-qualifying actions grant no XP": an activity with no
		// matching source produces nothing.
		registerSkill();
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		assertTrue(XpSourceRouter.plan(ActivityEvent.of(ActivityTypes.COMBAT,
				ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"))).isEmpty());
		assertEquals(0, data.skills().size());
	}
}
