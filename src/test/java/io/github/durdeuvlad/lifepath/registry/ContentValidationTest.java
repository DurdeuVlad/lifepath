package io.github.durdeuvlad.lifepath.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.ResourceDefinition;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * M7-5 fixture pack: one defect per class — missing ref, duplicate ref,
 * invalid range, impossible threshold, dependency cycle. Each must surface
 * in {@link ValidationReport} naming domain + file + field + problem.
 */
class ContentValidationTest {

	@AfterEach
	void reset() {
		LifepathContent.species().clear();
		LifepathContent.specializations().clear();
		LifepathContent.skills().clear();
		LifepathContent.abilities().clear();
		LifepathContent.resources().clear();
		LifepathContent.validateAll();
	}

	private static Identifier id(String path) {
		return Identifier.of("lifepath", path);
	}

	@Test
	void missingAbilityReferenceIsReported() {
		LifepathContent.species().register(id("s"),
				new SpeciesDefinition(id("s"), "S", SpeciesDefinition.Visibility.NORMAL,
						SpeciesDefinition.Selection.OPEN, List.of(id("ghost_ability")),
						List.of(), Map.of(), List.of(),
						Optional.empty(), Optional.empty()));

		ValidationReport report = LifepathContent.validateAll();

		assertTrue(report.hasErrors());
		assertTrue(report.issues().stream()
				.anyMatch(i -> i.domain().equals("species")
						&& i.message().contains("ghost_ability")));
		// The grouped detail also names the file + field + problem.
		assertTrue(report.detailLines().stream()
				.anyMatch(l -> l.contains("ghost_ability")));
	}

	@Test
	void duplicatePassiveRefIsReported() {
		LifepathContent.abilities().register(id("a"), ability(id("a")));
		Identifier dup = id("a");
		LifepathContent.species().register(id("s"),
				new SpeciesDefinition(id("s"), "S", SpeciesDefinition.Visibility.NORMAL,
						SpeciesDefinition.Selection.OPEN, List.of(dup, dup),
						List.of(), Map.of(), List.of(),
						Optional.empty(), Optional.empty()));

		ValidationReport report = LifepathContent.validateAll();

		assertTrue(report.detailLines().stream()
				.anyMatch(l -> l.contains("duplicate") && l.contains("lifepath:a")));
	}

	@Test
	void abilityInBothPassiveAndActiveIsReported() {
		LifepathContent.abilities().register(id("a"), ability(id("a")));
		LifepathContent.species().register(id("s"),
				new SpeciesDefinition(id("s"), "S", SpeciesDefinition.Visibility.NORMAL,
						SpeciesDefinition.Selection.OPEN, List.of(id("a")),
						List.of(id("a")), Map.of(), List.of(),
						Optional.empty(), Optional.empty()));

		ValidationReport report = LifepathContent.validateAll();

		assertTrue(report.detailLines().stream()
				.anyMatch(l -> l.contains("BOTH passive and active")));
	}

	@Test
	void outOfRangeMilestoneAndFloorAreReported() {
		LifepathContent.skills().register(id("s"), new SkillDefinition(id("s"), "S",
				SkillDefinition.Category.GATHERING, 10, Optional.empty(),
				List.of(new SkillDefinition.Milestone(99, "x", List.of())),
				List.of(), Optional.empty()));
		LifepathContent.specializations().register(id("sp"),
				new SpecializationDefinition(id("sp"), "SP",
						Map.of(id("s"), 42), Map.of(), Map.of(), Map.of(),
						Map.of(id("s"), 50), List.of()));

		ValidationReport report = LifepathContent.validateAll();

		assertTrue(report.detailLines().stream()
				.anyMatch(l -> l.contains("milestone level 99")));
		assertTrue(report.detailLines().stream()
				.anyMatch(l -> l.contains("starting_skills") && l.contains("42")));
		assertTrue(report.detailLines().stream()
				.anyMatch(l -> l.contains("floor 50")));
	}

	@Test
	void resourceCycleIsReported() {
		// A's band action modifies B; B's modifies A — a tick-loop ping-pong.
		LifepathContent.resources().register(id("a"), resourceWithAction(id("a"), id("b")));
		LifepathContent.resources().register(id("b"), resourceWithAction(id("b"), id("a")));

		ValidationReport report = LifepathContent.validateAll();

		assertTrue(report.detailLines().stream()
				.anyMatch(l -> l.contains("cyclic") && l.contains("lifepath:a")));
	}

	@Test
	void cleanContentProducesEmptyReport() {
		LifepathContent.skills().register(id("s"), new SkillDefinition(id("s"), "S",
				SkillDefinition.Category.GATHERING, 100, Optional.empty(),
				List.of(new SkillDefinition.Milestone(50, "x", List.of())),
				List.of(), Optional.empty()));

		ValidationReport report = LifepathContent.validateAll();

		assertFalse(report.hasErrors());
		assertEquals(0, report.issues().size());
		assertEquals("content validation clean — no issues", report.summaryLine());
	}

	private static AbilityDefinition ability(Identifier id) {
		return new AbilityDefinition(id, "A", true,
				new AbilityDefinition.Trigger(AbilityDefinition.Kind.PASSIVE, 20,
						List.of(), 1.0),
				AbilityDefinition.ConditionSet.NONE,
				new AbilityDefinition.SpecNode(LifepathMod.id("self"), new JsonObject()),
				List.of(), Optional.empty(), Optional.empty(), List.of());
	}

	private static ResourceDefinition resourceWithAction(Identifier id, Identifier target) {
		JsonObject params = new JsonObject();
		params.addProperty("resource", target.toString());
		params.addProperty("amount", 1.0);
		var node = new AbilityDefinition.SpecNode(LifepathMod.id("modify_resource"), params);
		return new ResourceDefinition(id, "R", 0.0, 100.0, 50.0, 0.0,
				List.of(new ResourceDefinition.Band("hot", 80.0, 100.0, List.of(),
						List.of(node))));
	}
}
