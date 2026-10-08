package com.dwurdy.lifepath.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityVocabulary;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.DietDefinition;
import com.dwurdy.lifepath.content.IdTagRef;
import com.dwurdy.lifepath.content.RelationDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.relation.DispositionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M5-4 Undead: restricted diet + mob neutrality + private Death Sight — all
 * through the generic {@code diet}/{@code relation} content domains and the
 * ability vocabulary. Live-entity paths (eatFood wrap, TargetPredicate gate)
 * compile-verified; everything decodable is pinned here.
 */
class UndeadSpeciesTest {
	private static final Path DATA = Path.of("src/main/resources/data/lifepath");
	private static final ResourceLocation UNDEAD = LifepathMod.id("undead");
	private static final ResourceLocation NECROPHAGE = LifepathMod.id("necrophage");
	private static final ResourceLocation UNDEAD_KIN = LifepathMod.id("undead_kin");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		LifepathContent.species().clear();
		LifepathContent.diets().clear();
		LifepathContent.relations().clear();
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		LifepathContent.species().clear();
		LifepathContent.diets().clear();
		LifepathContent.relations().clear();
	}

	private static <T> T decode(String domain, String name,
			com.mojang.serialization.Codec<T> codec) throws Exception {
		return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(
						Files.readString(DATA.resolve(domain + "/" + name + ".json"))))
				.result().orElseThrow(() -> new AssertionError(domain + "/" + name
						+ " failed to parse"));
	}

	@Test
	void dietDefDecodesAndMatches() throws Exception {
		DietDefinition def = DietDefinition.fromFile(NECROPHAGE,
				decode("diet", "necrophage", DietDefinition.DietFile.CODEC));
		assertEquals(1, def.allowed().size());
		// The shipped rule is a tag — exact-id matching is exercised on a
		// synthetic rule; tag matching needs a live registry (entry == null
		// fails closed).
		IdTagRef tagRule = def.allowed().get(0);
		assertTrue(tagRule.tagId() != null
						&& tagRule.tagId().equals(LifepathMod.id("undead_foods")),
				"diet allowed list should point at #lifepath:undead_foods");
		assertFalse(tagRule.matches(ResourceLocation.fromNamespaceAndPath("minecraft", "rotten_flesh"),
				null, Registries.ITEM), "tag rule without an entry fails closed");
		assertTrue(IdTagRef.parse("minecraft:rotten_flesh").matches(
				ResourceLocation.fromNamespaceAndPath("minecraft", "rotten_flesh"), null, Registries.ITEM));
		assertFalse(IdTagRef.parse("minecraft:rotten_flesh").matches(
				ResourceLocation.fromNamespaceAndPath("minecraft", "bread"), null, Registries.ITEM));
	}

	/** Beta-10: the ferrovore diet must declare non-food nutrition so
	 *  right-clicking ingots actually eats — a diet without it gates but
	 *  never feeds. */
	@Test
	void ferrovoreDietDeclaresNonFoodNutrition() throws Exception {
		DietDefinition def = DietDefinition.fromFile(
				LifepathMod.id("ferrovore"),
				decode("diet", "ferrovore", DietDefinition.DietFile.CODEC));
		assertEquals(3, def.nutrition());
		assertEquals(0.4f, def.saturationModifier(), 0.001f);
		// Diets without the fields decode to 0 → gate-only (back-compat).
		DietDefinition gateOnly = DietDefinition.fromFile(NECROPHAGE,
				decode("diet", "necrophage", DietDefinition.DietFile.CODEC));
		assertEquals(0, gateOnly.nutrition());
		assertEquals(0.0f, gateOnly.saturationModifier());
	}

	@Test
	void relationDefDecodesAndMatchesUndeadKin() throws Exception {
		RelationDefinition def = RelationDefinition.fromFile(UNDEAD_KIN,
				decode("relation", "undead_kin", RelationDefinition.RelationFile.CODEC));
		assertEquals(1, def.rules().size());
		assertEquals(RelationDefinition.Disposition.NEUTRAL,
				def.rules().get(0).disposition());
		// Tag rule: no registry entry → no match; a synthetic exact rule pins
		// the precedence contract (first match wins).
		assertNull(def.dispositionFor(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"), null));
		RelationDefinition exact = new RelationDefinition(UNDEAD_KIN, List.of(
				new RelationDefinition.Rule(IdTagRef.parse("minecraft:zombie"),
						RelationDefinition.Disposition.NEUTRAL),
				new RelationDefinition.Rule(IdTagRef.parse("#lifepath:undead"),
						RelationDefinition.Disposition.HOSTILE)));
		assertEquals(RelationDefinition.Disposition.NEUTRAL,
				exact.dispositionFor(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"), null));
		assertNull(exact.dispositionFor(ResourceLocation.fromNamespaceAndPath("minecraft", "bee"), null));
	}

	@Test
	void deathSightDecodesPrivateNightGated() throws Exception {
		AbilityDefinition def = LifepathContent.decodeAbility(
				LifepathMod.id("undead_death_sight"),
				decode("ability", "undead_death_sight",
						AbilityDefinition.AbilityFile.CODEC));
		assertTrue(AbilityVocabulary.unknownNodeTypes(def).isEmpty());
		assertEquals(AbilityDefinition.Kind.ACTIVE, def.trigger().kind());
		assertEquals(60.0, def.cooldown().orElseThrow().seconds());
		// Private highlight: the shipped visibility must NOT be "global" —
		// anything else resolves to caster-only in highlight_entities.
		assertEquals("self",
				def.actions().get(0).raw().get("visibility").getAsString());
		assertEquals(160, def.actions().get(0).raw().get("duration_ticks").getAsInt());
	}

	@Test
	void undeadSpeciesWiresDietRelationAndDeathSight() throws Exception {
		SpeciesDefinition def = SpeciesDefinition.fromFile(UNDEAD,
				decode("species", "undead",
						SpeciesDefinition.SpeciesDefinitionFile.CODEC));
		assertEquals(NECROPHAGE, def.dietRules().orElseThrow());
		assertEquals(UNDEAD_KIN, def.mobDispositions().orElseThrow());
		assertEquals(List.of(LifepathMod.id("undead_death_sight")), def.activeAbilities());
		assertEquals(List.of(LifepathMod.id("undead_sun_burn"),
						LifepathMod.id("undead_night_strength"),
						LifepathMod.id("undead_rot_touch")),
				def.passiveAbilities());

		// Service-level resolution: register the defs, set the species, the
		// services resolve through data.speciesId() — no player needed.
		LifepathContent.species().register(UNDEAD, def);
		LifepathContent.diets().register(NECROPHAGE,
				DietDefinition.fromFile(NECROPHAGE,
						decode("diet", "necrophage", DietDefinition.DietFile.CODEC)));
		LifepathContent.relations().register(UNDEAD_KIN,
				RelationDefinition.fromFile(UNDEAD_KIN,
						decode("relation", "undead_kin", RelationDefinition.RelationFile.CODEC)));
		data.setSpeciesId(UNDEAD);
		assertEquals(NECROPHAGE, DietService.dietOf(data).id());
		assertEquals(UNDEAD_KIN, DispositionService.relationOf(data).id());
		// A species with no rules resolves to null (vanilla behavior).
		data.setSpeciesId(LifepathMod.id("human"));
		assertNull(DietService.dietOf(data));
		assertNull(DispositionService.relationOf(data));
	}
}
