package io.github.durdeuvlad.lifepath.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActivityPipelineTest {
	@BeforeEach
	void setUp() {
		ActivityDispatcher.resetForTests();
	}

	private static Identifier id(String path) {
		return LifepathMod.id(path);
	}

	@Test
	void repetitionSignatureDistinguishesSources() {
		ActivityEvent stone = ActivityEvent.of(ActivityTypes.MINING, Identifier.of("minecraft", "stone"));
		ActivityEvent ore = ActivityEvent.of(ActivityTypes.MINING, Identifier.of("minecraft", "diamond_ore"));
		ActivityEvent farm = ActivityEvent.of(ActivityTypes.FARMING, Identifier.of("minecraft", "wheat"));
		assertNotEquals(stone.repetitionSignature(), ore.repetitionSignature());
		assertNotEquals(stone.repetitionSignature(), farm.repetitionSignature());
		// Same (type, source) repeated -> identical signature: anti-exploit bucketing works.
		assertEquals(stone.repetitionSignature(),
				ActivityEvent.of(ActivityTypes.MINING, Identifier.of("minecraft", "stone")).repetitionSignature());
	}

	@Test
	void factoriesProduceDocumentedShapes() {
		ActivityEvent f = ActivityEvents.farming(null, Identifier.of("minecraft", "wheat"), true);
		assertEquals(ActivityTypes.FARMING, f.type());
		assertEquals(Identifier.of("minecraft", "wheat"), f.sourceId());
		assertEquals("true", f.attributes().get("mature"));
		assertTrue(f.tags().contains(id("mature")));

		ActivityEvent s = ActivityEvents.smithing(null, Identifier.of("minecraft", "recipe/x"),
				Identifier.of("minecraft", "iron_sword"), "iron", Identifier.of("minecraft", "anvil"));
		assertEquals("iron", s.attributes().get("material_tier"));
		assertEquals("minecraft:anvil", s.attributes().get("workstation"));
		assertEquals(ActivityEvent.Cause.PLAYER, s.cause());
	}

	@Test
	void dispatcherRoutesByTypeAndToAny() {
		List<ActivityEvent> mining = new ArrayList<>();
		List<ActivityEvent> farming = new ArrayList<>();
		List<ActivityEvent> all = new ArrayList<>();
		ActivityDispatcher.register(ActivityTypes.MINING, mining::add);
		ActivityDispatcher.register(ActivityTypes.FARMING, farming::add);
		ActivityDispatcher.registerAny(all::add);

		ActivityEvent m = ActivityEvent.of(ActivityTypes.MINING, Identifier.of("minecraft", "stone"));
		ActivityEvent f = ActivityEvent.of(ActivityTypes.FARMING, Identifier.of("minecraft", "wheat"));
		ActivityDispatcher.publish(m);
		ActivityDispatcher.publish(f);

		assertEquals(List.of(m), mining);
		assertEquals(List.of(f), farming);
		assertEquals(List.of(m, f), all);
	}

	@Test
	void listenerExceptionsAreIsolated() {
		List<ActivityEvent> received = new ArrayList<>();
		ActivityDispatcher.register(ActivityTypes.MINING, e -> {
			throw new RuntimeException("bad listener");
		});
		ActivityDispatcher.register(ActivityTypes.MINING, received::add);
		ActivityDispatcher.publish(ActivityEvent.of(ActivityTypes.MINING, Identifier.of("minecraft", "stone")));
		assertEquals(1, received.size()); // the throw didn't eat the event for other listeners
	}

	@Test
	void eventCarriesAntiExploitMetadata() {
		long t = System.currentTimeMillis();
		ActivityEvent e = new ActivityEvent(null, ActivityTypes.MINING,
				Identifier.of("minecraft", "stone"), Set.of(id("ore")),
				ActivityEvent.Cause.PLAYER, t, Map.of("tier", "iron"));
		assertEquals(t, e.timestamp());
		assertEquals(ActivityEvent.Cause.PLAYER, e.cause());
		assertTrue(e.tags().contains(id("ore")));
		assertEquals("iron", e.attributes().get("tier"));
	}
}
