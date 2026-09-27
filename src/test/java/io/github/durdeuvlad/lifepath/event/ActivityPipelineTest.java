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

		// Canonical shape: sourceId = output id; tags = item tags + workstation
		// + smithing_workstations marker; attrs carry workstation + extras.
		ActivityEvent s = ActivityEvents.smithing(null, Identifier.of("minecraft", "iron_sword"),
				Set.of(id("smithing_tier_iron")), id("anvil"), Map.of("count", "1"));
		assertEquals(Identifier.of("minecraft", "iron_sword"), s.sourceId());
		assertEquals("lifepath:smithing|minecraft:iron_sword", s.repetitionSignature());
		assertEquals("lifepath:anvil", s.attributes().get("workstation"));
		assertEquals("1", s.attributes().get("count"));
		assertTrue(s.tags().contains(id("anvil")));
		assertTrue(s.tags().contains(id("smithing_workstations")));
		assertTrue(s.tags().contains(id("smithing_tier_iron")));
		assertEquals(ActivityEvent.Cause.PLAYER, s.cause());

		// Crafting (engineering feed): same canonical shape — output id +
		// item tags + count attr; emitted by CraftingResultSlotMixin.
		ActivityEvent c = ActivityEvents.crafting(null,
				Identifier.of("minecraft", "piston"),
				Set.of(Identifier.of("minecraft", "redstone")), Map.of("count", "1"),
				ActivityEvent.Cause.PLAYER);
		assertEquals(ActivityTypes.CRAFTING, c.type());
		assertEquals(Identifier.of("minecraft", "piston"), c.sourceId());
		assertTrue(c.tags().contains(Identifier.of("minecraft", "redstone")));
		assertEquals("1", c.attributes().get("count"));

		// Combat (M8-1 athletics feed): sourceId = killed entity type id,
		// tags = victim entity-type tags for xp_source per_tag weighting.
		ActivityEvent k = ActivityEvents.combat(null,
				Identifier.of("minecraft", "zombie"),
				Set.of(Identifier.of("minecraft", "undead")),
				ActivityEvent.Cause.PLAYER);
		assertEquals(ActivityTypes.COMBAT, k.type());
		assertEquals(Identifier.of("minecraft", "zombie"), k.sourceId());
		assertTrue(k.tags().contains(Identifier.of("minecraft", "undead")));
		assertEquals(ActivityEvent.Cause.PLAYER, k.cause());
	}

	/** M8-1: the shipped athletics xp_source resolves tagged kills as specific. */
	@Test
	void athleticsSourceResolvesCombatKills() throws Exception {
		var file = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
				java.nio.file.Path.of(
						"src/main/resources/data/lifepath/xp_source/athletics.json")));
		var def = io.github.durdeuvlad.lifepath.content.XpSourceDefinition.fromFile(
				id("athletics"), io.github.durdeuvlad.lifepath.content.XpSourceDefinition
						.XpSourceFile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, file)
						.getOrThrow(err -> new AssertionError("athletics xp_source: " + err)));
		ActivityEvent zombie = ActivityEvents.combat(null,
				Identifier.of("minecraft", "zombie"),
				Set.of(Identifier.of("minecraft", "undead")),
				ActivityEvent.Cause.PLAYER);
		assertTrue(def.matches(zombie));
		assertTrue(def.resolve(zombie.sourceId(), zombie.tags()).specific());
		assertEquals(0.5, def.resolve(zombie.sourceId(), zombie.tags()).amount(), 1e-6);
		// An untagged kill falls back to base_xp (still awards — combat is universal).
		assertEquals(0.4, def.resolve(Identifier.of("minecraft", "slime"),
				Set.of()).amount(), 1e-6);

		// Archery (M8-3): same payload shape as combat, distinct activity id —
		// emitted alongside combat when the killing blow is a projectile.
		ActivityEvent a = ActivityEvents.archery(null,
				Identifier.of("minecraft", "skeleton"),
				Set.of(Identifier.of("minecraft", "undead")),
				ActivityEvent.Cause.PLAYER);
		assertEquals(ActivityTypes.ARCHERY, a.type());
		assertEquals(Identifier.of("minecraft", "skeleton"), a.sourceId());
		assertTrue(a.tags().contains(Identifier.of("minecraft", "undead")));

		// Defence (M8-3): sourceId/tags describe the ATTACKER; the victim is
		// the xp subject. Cause is NON_PLAYER — mob-caused, not player action.
		ActivityEvent df = ActivityEvents.defence(null,
				Identifier.of("minecraft", "zombie"),
				Set.of(Identifier.of("minecraft", "undead")),
				ActivityEvent.Cause.NON_PLAYER);
		assertEquals(ActivityTypes.DEFENCE, df.type());
		assertEquals(Identifier.of("minecraft", "zombie"), df.sourceId());
		assertEquals(ActivityEvent.Cause.NON_PLAYER, df.cause());
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
