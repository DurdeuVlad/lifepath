package com.dwurdy.lifepath.compat.overgeared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.LifepathMod;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.event.ActivityTypes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * M7-1 adapter contract: a normalized crafting event whose result is a forged
 * item (overgeared id or {@code #lifepath:forged_outputs} tag) is republished
 * as canonical smithing activity; everything else passes through untouched.
 * The Overgeared mod itself is never loaded — the adapter is pure data-side
 * translation (TIMELINE §10 levels 2–3).
 */
class OvergearedCompatTest {
	private final List<ActivityEvent> seen = new CopyOnWriteArrayList<>();
	private final ActivityDispatcher.Listener tap = seen::add;

	@AfterEach
	void tearDown() {
		ActivityDispatcher.resetForTests();
	}

	private void wireAdapter() {
		// register() self-gates on isModLoaded (false headless) — wire() is
		// the translation surface under test.
		new OvergearedCompat().wire();
		ActivityDispatcher.registerAny(tap);
	}

	@Test
	void overgearedResultRepublishesAsSmithing() {
		wireAdapter();
		ResourceLocation forged = ResourceLocation.fromNamespaceAndPath("overgeared", "steel_sword");
		ActivityDispatcher.publish(ActivityEvents.crafting(null, forged));
		assertEquals(2, seen.size(), "original crafting + translated smithing");
		ActivityEvent re = seen.stream()
				.filter(e -> e.type() == ActivityTypes.SMITHING).findFirst()
				.orElseThrow();
		assertEquals(ActivityTypes.SMITHING, re.type());
		assertEquals(forged, re.sourceId());
		assertEquals("overgeared", re.attributes().get("compat"));
		assertTrue(re.tags().contains(OvergearedCompat.FORGE_WORKSTATION));
	}

	@Test
	void datapackTagAlsoTriggersTranslation() {
		// Pack authors opt foreign results in via #lifepath:forged_outputs —
		// the id namespace needn't be overgeared.
		wireAdapter();
		ActivityEvent ev = new ActivityEvent(null, ActivityTypes.CRAFTING,
				ResourceLocation.fromNamespaceAndPath("somefuturemod", "forged_plate"),
				Set.of(LifepathMod.id("forged_outputs")),
				ActivityEvent.Cause.PLAYER, 0L, Map.of());
		ActivityDispatcher.publish(ev);
		assertEquals(2, seen.size());
		assertTrue(seen.stream()
				.anyMatch(e -> e.type() == ActivityTypes.SMITHING));
	}

	@Test
	void vanillaAndUnrelatedEventsAreUntouched() {
		wireAdapter();
		ActivityDispatcher.publish(ActivityEvents.crafting(null,
				ResourceLocation.fromNamespaceAndPath("minecraft", "crafting_table")));
		ActivityDispatcher.publish(new ActivityEvent(null,
				ActivityTypes.MINING, ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ore"),
				Set.of(), ActivityEvent.Cause.PLAYER, 0L, Map.of()));
		assertEquals(2, seen.size(), "no translations for unrelated events");
		assertTrue(seen.stream().allMatch(e -> e.type() != ActivityTypes.SMITHING));
	}

}
