package com.dwurdy.lifepath.species;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityEngine;
import com.dwurdy.lifepath.ability.AbilityVocabulary;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.DietDefinition;
import com.dwurdy.lifepath.content.RelationDefinition;
import com.dwurdy.lifepath.content.ResourceDefinition;
import com.dwurdy.lifepath.content.SkillDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import com.dwurdy.lifepath.content.XpSourceDefinition;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.registry.LifepathContent;
import com.dwurdy.lifepath.specialization.SpecializationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
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
	private static final Set<ResourceLocation> KNOWN_DANGLING_SIGNATURES = Set.of();

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
		LifepathContent.unlocks().clear();
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
		LifepathContent.unlocks().clear();
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
					com.dwurdy.lifepath.content.ConditionDefinition.fromFile(
							LifepathMod.id(n), decodeFile("condition", n,
									com.dwurdy.lifepath.content.ConditionDefinition
											.ConditionFile.CODEC)));
		for (String n : names("attunement"))
			LifepathContent.attunements().register(LifepathMod.id(n),
					com.dwurdy.lifepath.content.AttunementDefinition.fromFile(
							LifepathMod.id(n), decodeFile("attunement", n,
									com.dwurdy.lifepath.content.AttunementDefinition
											.AttunementFile.CODEC)));
		for (String n : names("unlock"))
			LifepathContent.unlocks().register(LifepathMod.id(n),
					com.dwurdy.lifepath.content.UnlockDefinition.fromFile(
							LifepathMod.id(n), decodeFile("unlock", n,
									com.dwurdy.lifepath.content.UnlockDefinition
											.UnlockFile.CODEC)));
	}

	@Test
	void milestoneContentChecklist() throws Exception {
		loadEverything();
		for (String s : List.of("human", "sylvian", "iceborn", "undead",
				"enderian", "amphibian", "dragonborn", "automaton",
				"hellborn", "anima", "dwarf", "goliath",
				"phoenix", "phantom", "celestial"))
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
		assertEquals(15, LifepathContent.species().size());
		for (String s : List.of("vampirism", "lycanthropy"))
			assertTrue(LifepathContent.conditions().contains(LifepathMod.id(s)),
					"missing condition " + s);
		for (String s : List.of("air", "earth", "lightning"))
			assertTrue(LifepathContent.attunements().contains(LifepathMod.id(s)),
					"missing attunement " + s);
		// M9-3: the load resource ships and carries the four spec bands.
		var load = LifepathContent.resources().get(LifepathMod.id("load"));
		assertTrue(load != null, "missing lifepath:load resource");
		assertEquals(4, load.bands().size());
	}

	@Test
	void everyCrossReferenceResolvesOrIsTracked() throws Exception {
		loadEverything();
		Set<ResourceLocation> dangling = new HashSet<>();
		for (SpeciesDefinition sp : LifepathContent.species().all().values()) {
			for (ResourceLocation a : Stream.concat(
					sp.passiveAbilities().stream(), sp.activeAbilities().stream()).toList())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			for (ResourceLocation r : sp.resources())
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
			for (ResourceLocation a : spec.signatureRefs())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			Set<ResourceLocation> skillRefs = new HashSet<>();
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
			for (ResourceLocation a : cond.abilities())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			for (var stage : cond.stages())
				for (ResourceLocation a : stage.abilities())
					if (!LifepathContent.abilities().contains(a)) dangling.add(a);
			for (ResourceLocation r : cond.resources())
				if (!LifepathContent.resources().contains(r)) dangling.add(r);
			cond.dietRules().ifPresent(d -> {
				if (!LifepathContent.diets().contains(d)) dangling.add(d);
			});
		}
		// M9-2: attunement → ability refs resolve too.
		for (var att : LifepathContent.attunements().all().values())
			for (ResourceLocation a : att.abilities())
				if (!LifepathContent.abilities().contains(a)) dangling.add(a);
		// M9-4: unlock → gated-content refs resolve (today: species ids).
		for (var unlock : LifepathContent.unlocks().all().values())
			for (ResourceLocation content : unlock.unlocks())
				if (!LifepathContent.species().contains(content)) dangling.add(content);

		assertEquals(KNOWN_DANGLING_SIGNATURES, dangling,
				"only the tracked spec-signature gap may dangle");
	}

	/**
	 * M9-4: the three special species are data-defined, hidden from the
	 * picker, and gated on {@code selection:"unlocked"} — and every unlock
	 * source type the framework supports ships a working example.
	 */
	@Test
	void specialSpeciesAreHiddenAndUnlockGated() throws Exception {
		loadEverything();
		for (String s : List.of("phoenix", "phantom", "celestial")) {
			SpeciesDefinition sp = LifepathContent.species().get(LifepathMod.id(s));
			assertTrue(sp != null, "missing " + s);
			assertEquals(SpeciesDefinition.Visibility.HIDDEN, sp.visibility(),
					s + " must be hidden from the picker");
			assertEquals(SpeciesDefinition.Selection.UNLOCKED, sp.selection(),
					s + " must gate on a held unlock");
		}
		// Every special species is reachable through a data-declared unlock.
		Set<ResourceLocation> gated = new HashSet<>();
		for (var u : LifepathContent.unlocks().all().values())
			gated.addAll(u.unlocks());
		for (String s : List.of("phoenix", "phantom", "celestial"))
			assertTrue(gated.contains(LifepathMod.id(s)),
					s + " has no unlock def granting it");
		// One working example per source type the issue requires.
		Set<String> sourceTypes = new HashSet<>();
		for (var u : LifepathContent.unlocks().all().values())
			for (var rule : u.sources())
				sourceTypes.add(rule.type());
		for (String t : List.of("item", "advancement", "event", "admin"))
			assertTrue(sourceTypes.contains(t), "no unlock ships a " + t + " source");
	}

	/** M9-4: Phantom's phase toggle uses only existing generic primitives. */
	@Test
	void phantomPhaseToggleIsDataOnGenericPrimitives() throws Exception {
		loadEverything();
		SpeciesDefinition phantom = LifepathContent.species().get(LifepathMod.id("phantom"));
		assertTrue(phantom != null);
		// The toggle target materializes through the species resources list.
		assertTrue(phantom.resources().contains(LifepathMod.id("phantom_form")));
		AbilityDefinition phase = LifepathContent.abilities()
				.get(LifepathMod.id("phantom_phase"));
		assertTrue(phase != null, "missing phantom_phase ability");
	}

	@Test
	void speciesAndSpecsComposeOrthogonally() throws Exception {
		loadEverything();
		// Every registered species — new species get this check for free.
		for (ResourceLocation speciesId : LifepathContent.species().all().keySet()) {
			SpeciesDefinition species = LifepathContent.species().get(speciesId);
			Set<ResourceLocation> innate = new HashSet<>();
			innate.addAll(species.passiveAbilities());
			innate.addAll(species.activeAbilities());
			// Every registered specialization — new presets get this check for free.
			for (ResourceLocation specId : LifepathContent.specializations().all().keySet()) {
				PlayerCharacterData d = PlayerCharacterData.createDefault();
				d.setSpeciesId(speciesId);
				assertEquals(SpecializationService.ApplyResult.APPLIED,
						SpecializationService.apply(d, specId));
				Set<ResourceLocation> owned = AbilityEngine.ownedAbilities(d);
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
