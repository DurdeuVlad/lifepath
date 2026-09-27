package io.github.durdeuvlad.lifepath.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.ConditionDefinition;
import io.github.durdeuvlad.lifepath.content.ResourceDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M9-1 service semantics — acquire/cure/advance/event-counting on plain
 * {@link PlayerCharacterData} (no entities; the {@code player} param is
 * {@code null}, which the service treats as "no dirty-mark needed").
 */
class ConditionServiceTest {
	private static final ResourceLocation COND = LifepathMod.id("test_curse");
	private static final ResourceLocation RES = LifepathMod.id("test_blood");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		LifepathContent.conditions().register(COND, new ConditionDefinition(
				COND, "Test Curse", Optional.empty(),
				List.of(LifepathMod.id("base_ability")),
				Optional.empty(), List.of(RES),
				List.of(
						new ConditionDefinition.Stage("early",
								List.of(), List.of(LifepathMod.id("combat")), 2,
								Optional.empty()),
						new ConditionDefinition.Stage("late",
								List.of(LifepathMod.id("late_ability")), List.of(),
								1, Optional.empty())),
				List.of(), List.of()));
		LifepathContent.resources().register(RES, new ResourceDefinition(
				RES, 0, 100, 50, -0.1, List.of()));
	}

	@AfterEach
	void tearDown() {
		LifepathContent.conditions().clear();
		LifepathContent.resources().clear();
	}

	private static ActivityEvent event(ResourceLocation type) {
		return new ActivityEvent(null, type, LifepathMod.id("src"),
				Set.of(), ActivityEvent.Cause.PLAYER, 0L, Map.of());
	}

	@Test
	void acquireMaterializesDeclaredResources() {
		assertTrue(ConditionService.acquire(data, null, COND, 1000L));
		assertTrue(data.conditions().contains(COND));
		assertEquals(0, data.conditionState(COND).stage());
		assertEquals(1000L, data.conditionState(COND).stageStartedAtMs());
		// Declared resource materialized at its definition default.
		assertEquals(50.0, data.resources().get(RES).current());
	}

	@Test
	void acquireIsIdempotentAndUnknownIdsFailClosed() {
		assertTrue(ConditionService.acquire(data, null, COND, 1L));
		assertFalse(ConditionService.acquire(data, null, COND, 2L));
		assertFalse(ConditionService.acquire(data, null,
				LifepathMod.id("unregistered"), 1L));
	}

	@Test
	void cureDropsStateAndDeclaredResources() {
		ConditionService.acquire(data, null, COND, 1L);
		assertTrue(ConditionService.cure(data, null, COND));
		assertFalse(data.conditions().contains(COND));
		assertNull(data.resources().get(RES));
		assertFalse(ConditionService.cure(data, null, COND));
	}

	@Test
	void eventCountingAdvancesAtDeclaredCount() {
		ConditionService.acquire(data, null, COND, 0L);
		ConditionService.onActivity(data, null, event(LifepathMod.id("combat")), 10L);
		assertEquals(0, data.conditionState(COND).stage());
		assertEquals(1, data.conditionState(COND).eventProgress());
		ConditionService.onActivity(data, null, event(LifepathMod.id("combat")), 20L);
		// Second matching event hits advance_count=2 → stage 1, counter reset.
		assertEquals(1, data.conditionState(COND).stage());
		assertEquals(0, data.conditionState(COND).eventProgress());
		assertEquals(20L, data.conditionState(COND).stageStartedAtMs());
	}

	@Test
	void nonMatchingEventsDoNotCount() {
		ConditionService.acquire(data, null, COND, 0L);
		ConditionService.onActivity(data, null, event(LifepathMod.id("mining")), 10L);
		assertEquals(0, data.conditionState(COND).eventProgress());
	}

	@Test
	void lastStageNeverAdvances() {
		ConditionService.acquire(data, null, COND, 0L);
		ConditionService.advance(data, null, COND, 5L); // → stage 1 (last)
		assertFalse(ConditionService.advance(data, null, COND, 6L));
		assertEquals(1, data.conditionState(COND).stage());
	}

	@Test
	void tickAdvancesOnElapsedSeconds() {
		ResourceLocation timed = LifepathMod.id("timed_curse");
		LifepathContent.conditions().register(timed, new ConditionDefinition(
				timed, "Timed", Optional.empty(), List.of(), Optional.empty(),
				List.of(),
				List.of(
						new ConditionDefinition.Stage("s0", List.of(), List.of(),
								1, Optional.of(60L)),
						new ConditionDefinition.Stage("s1", List.of(), List.of(),
								1, Optional.empty())),
				List.of(), List.of()));
		ConditionService.acquire(data, null, timed, 0L);
		ConditionService.tick(data, null, 59_999L);
		assertEquals(0, data.conditionState(timed).stage());
		ConditionService.tick(data, null, 60_000L);
		assertEquals(1, data.conditionState(timed).stage());
	}

	@Test
	void activeAbilitiesComposeBasePlusCumulativeStages() {
		assertTrue(ConditionService.activeAbilities(data).isEmpty());
		ConditionService.acquire(data, null, COND, 0L);
		assertEquals(List.of(LifepathMod.id("base_ability")),
				ConditionService.activeAbilities(data));
		ConditionService.advance(data, null, COND, 5L);
		assertEquals(List.of(LifepathMod.id("base_ability"),
						LifepathMod.id("late_ability")),
				ConditionService.activeAbilities(data));
	}

	@Test
	void outOfRangeStageIndexClamps() {
		ConditionService.acquire(data, null, COND, 0L);
		// Corrupt/crafted state — stage beyond the def clamps, never throws.
		data.putCondition(COND, new ConditionState(99, 0L, 0));
		assertEquals(List.of(LifepathMod.id("base_ability"),
						LifepathMod.id("late_ability")),
				ConditionService.activeAbilities(data));
		// Held conditions whose def unloaded survive a reload without error.
		LifepathContent.conditions().clear();
		assertTrue(ConditionService.activeAbilities(data).isEmpty());
		assertFalse(ConditionService.cure(data, null, COND));
	}
}
