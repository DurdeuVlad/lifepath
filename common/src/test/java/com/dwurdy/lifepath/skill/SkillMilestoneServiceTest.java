package com.dwurdy.lifepath.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.content.SkillDefinition;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Crossing math for milestone grants (B5) — dedupe + window semantics. */
class SkillMilestoneServiceTest {

	private static SkillDefinition def(List<SkillDefinition.Milestone> milestones) {
		return new SkillDefinition(LifepathMod.id("test"), "Test",
				SkillDefinition.Category.GATHERING, 100, Optional.empty(),
				milestones, List.of(), Optional.empty());
	}

	private static SkillDefinition.Milestone m(int level, String... refs) {
		return new SkillDefinition.Milestone(level, "k",
				java.util.Arrays.stream(refs)
						.map(ResourceLocation::parse).toList());
	}

	@Test
	void grantsOnlyCrossedMilestones() {
		SkillDefinition d = def(List.of(
				m(20, "a"), m(40, "b"), m(60, "c")));
		// crossing 15 -> 45 catches 20 and 40, not 60; boundary is (old, new]
		assertEquals(List.of(ResourceLocation.parse("a"), ResourceLocation.parse("b")),
				SkillMilestoneService.grantsFor(d, 15, 45));
		assertTrue(SkillMilestoneService.grantsFor(d, 45, 45).isEmpty());
		assertEquals(List.of(ResourceLocation.parse("c")),
				SkillMilestoneService.grantsFor(d, 45, 60));
	}

	@Test
	void dedupesSharedRefs() {
		SkillDefinition d = def(List.of(
				m(20, "shared"), m(25, "shared"), m(40, "b")));
		assertEquals(List.of(ResourceLocation.parse("shared"), ResourceLocation.parse("b")),
				SkillMilestoneService.grantsFor(d, 0, 60));
	}

	@Test
	void milestoneAtExactOldLevelDoesNotRegrant() {
		// decay-and-relearn: re-crossing 20 from 20 -> 21 shouldn't refire
		SkillDefinition d = def(List.of(m(20, "a")));
		assertTrue(SkillMilestoneService.grantsFor(d, 20, 21).isEmpty());
		assertEquals(List.of(ResourceLocation.parse("a")),
				SkillMilestoneService.grantsFor(d, 19, 21));
	}
}
