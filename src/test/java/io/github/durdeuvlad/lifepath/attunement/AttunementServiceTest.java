package io.github.durdeuvlad.lifepath.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.AttunementDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M9-2 service semantics — attune/unattune on plain {@link
 * PlayerCharacterData} with {@code player = null} (no dirty-mark needed).
 */
class AttunementServiceTest {
	private static final Identifier ATT = LifepathMod.id("test_affinity");

	private PlayerCharacterData data;

	@BeforeEach
	void setUp() {
		data = PlayerCharacterData.createDefault();
		LifepathContent.attunements().register(ATT, new AttunementDefinition(
				ATT, "Test Affinity", Optional.empty(),
				List.of(LifepathMod.id("affinity_power")),
				List.of(new AttunementDefinition.AcquisitionRule("event",
						Optional.empty(), Optional.empty(), Optional.empty(),
						Optional.of(LifepathMod.id("mining")), 1.0, true)),
				List.of()));
	}

	@AfterEach
	void tearDown() {
		LifepathContent.attunements().clear();
	}

	private static ActivityEvent event(Identifier type) {
		return new ActivityEvent(null, type, LifepathMod.id("src"),
				Set.of(), ActivityEvent.Cause.PLAYER, 0L, Map.of());
	}

	@Test
	void attuneIsIdempotentAndUnknownIdsFailClosed() {
		assertTrue(AttunementService.attune(data, null, ATT));
		assertTrue(data.attunements().contains(ATT));
		assertFalse(AttunementService.attune(data, null, ATT));
		assertFalse(AttunementService.attune(data, null,
				LifepathMod.id("unregistered")));
	}

	@Test
	void unattuneRemovesOnlyHeldAttunements() {
		assertFalse(AttunementService.unattune(data, null, ATT));
		AttunementService.attune(data, null, ATT);
		assertTrue(AttunementService.unattune(data, null, ATT));
		assertFalse(data.attunements().contains(ATT));
	}

	@Test
	void eventRuleAcquiresOnMatchingActivity() {
		AttunementService.onActivity(data, null, event(LifepathMod.id("fishing")), 0L);
		assertFalse(data.attunements().contains(ATT));
		AttunementService.onActivity(data, null, event(LifepathMod.id("mining")), 0L);
		assertTrue(data.attunements().contains(ATT));
		// Once held, further matching events are no-ops (no re-add churn).
		AttunementService.onActivity(data, null, event(LifepathMod.id("mining")), 1L);
		assertEquals(1, data.attunements().size());
	}

	@Test
	void activeAbilitiesGraftAttunementAbilities() {
		assertTrue(AttunementService.activeAbilities(data).isEmpty());
		AttunementService.attune(data, null, ATT);
		assertEquals(List.of(LifepathMod.id("affinity_power")),
				AttunementService.activeAbilities(data));
		// Unloaded def → held id survives the reload, contributes nothing.
		LifepathContent.attunements().clear();
		assertTrue(AttunementService.activeAbilities(data).isEmpty());
		assertTrue(data.attunements().contains(ATT));
	}
}
