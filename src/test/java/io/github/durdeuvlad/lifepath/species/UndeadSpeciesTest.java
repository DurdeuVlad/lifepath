package io.github.durdeuvlad.lifepath.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.DietDefinition;
import io.github.durdeuvlad.lifepath.content.IdTagRef;
import io.github.durdeuvlad.lifepath.content.RelationDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.relation.DispositionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
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
	private static final Identifier UNDEAD = LifepathMod.id("undead");
	private static final Identifier NECROPHAGE = LifepathMod.id("necrophage");
	private static final Identifier UNDEAD_KIN = LifepathMod.id("undead_kin");

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
		assertFalse(tagRule.matches(Identifier.of("minecraft", "rotten_flesh"),
				null, RegistryKeys.ITEM), "tag rule without an entry fails closed");
		assertTrue(IdTagRef.parse("minecraft:rotten_flesh").matches(
				Identifier.of("minecraft", "rotten_flesh"), null, RegistryKeys.ITEM));
		assertFalse(IdTagRef.parse("minecraft:rotten_flesh").matches(
				Identifier.of("minecraft", "bread"), null, RegistryKeys.ITEM));
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
		assertNull(def.dispositionFor(Identifier.of("minecraft", "zombie"), null));
		RelationDefinition exact = new RelationDefinition(UNDEAD_KIN, List.of(
				new RelationDefinition.Rule(IdTagRef.parse("minecraft:zombie"),
						RelationDefinition.Disposition.NEUTRAL),
				new RelationDefinition.Rule(IdTagRef.parse("#lifepath:undead"),
						RelationDefinition.Disposition.HOSTILE)));
		assertEquals(RelationDefinition.Disposition.NEUTRAL,
				exact.dispositionFor(Identifier.of("minecraft", "zombie"), null));
		assertNull(exact.dispositionFor(Identifier.of("minecraft", "bee"), null));
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
		assertTrue(def.passiveAbilities().isEmpty());

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
