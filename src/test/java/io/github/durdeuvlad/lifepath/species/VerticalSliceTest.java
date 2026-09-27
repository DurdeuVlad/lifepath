package io.github.durdeuvlad.lifepath.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityEngine;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.DietDefinition;
import io.github.durdeuvlad.lifepath.content.RelationDefinition;
import io.github.durdeuvlad.lifepath.content.ResourceDefinition;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.content.XpSourceDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.specialization.SpecializationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M5-5: the vertical slice as executable content completeness — every shipped
 * file in every domain parses through the real codec path, and every
 * cross-reference between domains resolves against the registries (mirroring
 * live load order: resources before abilities). The four known dangling
 * specialization signatures are pinned as the allowed-defect set so any NEW
 * dangling reference fails this test.
 */
class VerticalSliceTest {
	private static final Path DATA = Path.of("src/main/resources/data/lifepath");

	/**
	 * Dangling refs the slice is allowed to have. Empty since #96 — the spec
	 * signature abilities shipped; any NEW dangling reference fails the test.
	 */
	private static final Set<Identifier> KNOWN_DANGLING_SIGNATURES = Set.of();

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		AbilityVocabulary.init();
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
		LifepathContent.skills().clear();
		LifepathContent.xpSources().clear();
		LifepathContent.resources().clear();
		LifepathContent.abilities().clear();
		LifepathContent.diets().clear();
		LifepathContent.relations().clear();
		LifepathContent.conditions().clear();
		LifepathContent.attunements().clear();
	}

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
		AbilityVocabulary.resetForTests();
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
		LifepathContent.skills().clear();
		LifepathContent.xpSources().clear();
		LifepathContent.resources().clear();
		LifepathContent.abilities().clear();
		LifepathContent.diets().clear();
		LifepathContent.relations().clear();
		LifepathContent.conditions().clear();
		LifepathContent.attunements().clear();
	}

	private static <T> T decodeFile(String domain, String name, Codec<T> codec)
			throws Exception {
		return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(
						DATA.resolve(domain + "/" + name + ".json"))))
				.result().orElseThrow(() -> new AssertionError(
						domain + "/" + name + " failed to parse"));
	}

	private static List<String> names(String domain) throws Exception {
		try (Stream<Path> files = Files.list(DATA.resolve(domain))) {
			return files.filter(p -> p.toString().endsWith(".json"))
					.map(p -> p.getFileName().toString().replace(".json", ""))
					.sorted().toList();
		}
	}

	/** Loads every shipped definition into the registries, in live load order. */
	private void loadEverything() throws Exception {
		for (String n : names("skill"))
			LifepathContent.skills().register(LifepathMod.id(n),
					SkillDefinition.fromFile(LifepathMod.id(n),
							decodeFile("skill", n, SkillDefinition.SkillDefinitionFile.CODEC)));
		for (String n : names("xp_source"))
			LifepathContent.xpSources().register(LifepathMod.id(n),
					XpSourceDefinition.fromFile(LifepathMod.id(n),
							decodeFile("xp_source", n, XpSourceDefinition.XpSourceFile.CODEC)));
		for (String n : names("resource"))
			LifepathContent.resources().register(LifepathMod.id(n),
					LifepathContent.decodeResource(LifepathMod.id(n),
							decodeFile("resource", n, ResourceDefinition.ResourceFile.CODEC)));
		for (String n : names("ability"))
			LifepathContent.abilities().register(LifepathMod.id(n),
					LifepathContent.decodeAbility(LifepathMod.id(n),
							decodeFile("ability", n, AbilityDefinition.AbilityFile.CODEC)));
		for (String n : names("diet"))
			LifepathContent.diets().register(LifepathMod.id(n),
					DietDefinition.fromFile(LifepathMod.id(n),
							decodeFile("diet", n, DietDefinition.DietFile.CODEC)));
		for (String n : names("relation"))
			LifepathContent.relations().register(LifepathMod.id(n),
					RelationDefinition.fromFile(LifepathMod.id(n),
							decodeFile("relation", n, RelationDefinition.RelationFile.CODEC)));
		for (String n : names("species"))
			LifepathContent.species().register(LifepathMod.id(n),
					SpeciesDefinition.fromFile(LifepathMod.id(n),
							decodeFile("species", n, SpeciesDefinition.SpeciesDefinitionFile.CODEC)));
		for (String n : names("specialization"))
			LifepathContent.specializations().register(LifepathMod.id(n),
					SpecializationDefinition.fromFile(LifepathMod.id(n),
							decodeFile("specialization", n,
									SpecializationDefinition.SpecializationDefinitionFile.CODEC)));
		for (String n : names("condition"))
			LifepathContent.conditions().register(LifepathMod.id(n),
					io.github.durdeuvlad.lifepath.content.ConditionDefinition.fromFile(
							LifepathMod.id(n), decodeFile("condition", n,
									io.github.durdeuvlad.lifepath.content.ConditionDefinition
											.ConditionFile.CODEC)));
		for (String n : names("attunement"))
			LifepathContent.attunements().register(LifepathMod.id(n),
					io.github.durdeuvlad.lifepath.content.AttunementDefinition.fromFile(
							LifepathMod.id(n), decodeFile("attunement", n,
									io.github.durdeuvlad.lifepath.content.AttunementDefinition
											.AttunementFile.CODEC)));
	}

	@Test
	void milestoneContentChecklist() throws Exception {
		loadEverything();
		for (String s : List.of("human", "sylvian", "iceborn", "undead",
				"enderian", "amphibian", "dragonborn", "automaton",
				"hellborn", "anima", "dwarf", "goliath"))
			assertTrue(LifepathContent.species().contains(LifepathMod.id(s)),
					"missing species " + s);
		for (String s : List.of("miner", "farmer", "blacksmith", "fisherman",
				"lumberjack", "hunter", "engineer", "herbalist", "cook",
				"explorer", "laborer", "scholar"))
			assertTrue(LifepathContent.specializations().contains(LifepathMod.id(s)),
					"missing specialization " + s);
		for (String s : List.of("mining", "farming", "smithing", "fishing",
				"foraging", "engineering", "athletics", "woodcutting",
				"hunting", "cooking", "archery", "defence", "scholarship"))
			assertTrue(LifepathContent.skills().contains(LifepathMod.id(s)),
					"missing skill " + s);
		assertEquals(12, LifepathContent.species().size());
		for (String s : List.of("vampirism", "lycanthropy"))
			assertTrue(LifepathContent.conditions().contains(LifepathMod.id(s)),
					"missing condition " + s);
		for (String s : List.of("air", "earth", "lightning"))
			assertTrue(LifepathContent.attunements().contains(LifepathMod.id(s)),
					"missing attunement " + s);
	}

	@Test
	void everyCrossReferenceResolvesOrIsTracked() throws Exception {
		loadEverything();
		Set<Identifier> dangling = new HashSet<>();
		for (SpeciesDefinition sp : LifepathContent.species().all().values()) {
			for (Identifier a : Stream.concat(
					sp.passiveAbilities().stream(), sp.activeAbilities().stream()).toList())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			for (Identifier r : sp.resources())
				if (!LifepathContent.resources().contains(r)) dangling.add(r);
			sp.dietRules().ifPresent(d -> {
				if (!LifepathContent.diets().contains(d)) dangling.add(d);
			});
			sp.mobDispositions().ifPresent(d -> {
				if (!LifepathContent.relations().contains(d)) dangling.add(d);
			});
			sp.minAptitudes().keySet().forEach(s -> {
				if (!LifepathContent.skills().contains(s)) dangling.add(s);
			});
		}
		for (SpecializationDefinition spec : LifepathContent.specializations().all().values()) {
			for (Identifier a : spec.signatureRefs())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			Set<Identifier> skillRefs = new HashSet<>();
			skillRefs.addAll(spec.startingSkills().keySet());
			skillRefs.addAll(spec.aptitudes().keySet());
			skillRefs.addAll(spec.xpModifiers().keySet());
			skillRefs.addAll(spec.decayModifiers().keySet());
			skillRefs.addAll(spec.protectedFloors().keySet());
			skillRefs.forEach(s -> {
				if (!LifepathContent.skills().contains(s)) dangling.add(s);
			});
		}
		for (XpSourceDefinition xs : LifepathContent.xpSources().all().values())
			if (!LifepathContent.skills().contains(xs.skill())) dangling.add(xs.skill());
		// M9-1: condition → ability/resource/diet refs resolve too.
		for (var cond : LifepathContent.conditions().all().values()) {
			for (Identifier a : cond.abilities())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			for (var stage : cond.stages())
				for (Identifier a : stage.abilities())
					if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			for (Identifier r : cond.resources())
				if (!LifepathContent.resources().contains(r)) dangling.add(r);
			cond.dietRules().ifPresent(d -> {
				if (!LifepathContent.diets().contains(d)) dangling.add(d);
			});
		}
		// M9-2: attunement → ability refs resolve too.
		for (var att : LifepathContent.attunements().all().values())
			for (Identifier a : att.abilities())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);

		assertEquals(KNOWN_DANGLING_SIGNATURES, dangling,
				"only the tracked spec-signature gap may dangle");
	}

	@Test
	void speciesAndSpecsComposeOrthogonally() throws Exception {
		loadEverything();
		// Every registered species — new species get this check for free.
		for (Identifier speciesId : LifepathContent.species().all().keySet()) {
			SpeciesDefinition species = LifepathContent.species().get(speciesId);
			Set<Identifier> innate = new HashSet<>();
			innate.addAll(species.passiveAbilities());
			innate.addAll(species.activeAbilities());
			// Every registered specialization — new presets get this check for free.
			for (Identifier specId : LifepathContent.specializations().all().keySet()) {
				PlayerCharacterData d = PlayerCharacterData.createDefault();
				d.setSpeciesId(speciesId);
				assertEquals(SpecializationService.ApplyResult.APPLIED,
						SpecializationService.apply(d, specId));
				Set<Identifier> owned = AbilityEngine.ownedAbilities(d);
				assertTrue(owned.containsAll(innate),
						speciesId + " lost innate abilities under " + specId);
				// Species and specialization are orthogonal axes: every pair
				// composes with no species-specific gating.
			}
		}
	}

	@Test
	void specSignatureAbilitiesResolveAndApply() throws Exception {
		loadEverything();
		PlayerCharacterData d = PlayerCharacterData.createDefault();
		d.setSpeciesId(LifepathMod.id("human"));
		assertEquals(SpecializationService.ApplyResult.APPLIED,
				SpecializationService.apply(d, LifepathMod.id("miner")));
		// The signature is a real owned ability once its file ships (#96).
		assertTrue(LifepathContent.abilities()
				.contains(LifepathMod.id("deepvein_sense_i")));
		assertTrue(AbilityEngine.ownedAbilities(d)
				.contains(LifepathMod.id("deepvein_sense_i")));
	}
}
