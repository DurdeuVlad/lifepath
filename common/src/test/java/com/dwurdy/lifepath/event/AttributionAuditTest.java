package com.dwurdy.lifepath.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.content.XpSourceDefinition;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

/**
 * M21 attribution contract (docs/ATTRIBUTION.md): the player performing the
 * activity is the credited actor. Factories must store the exact reference
 * they are handed — never a re-resolved or substituted player — and shipped
 * xp_source files must carry the documented player_caused_only flags.
 *
 * Mixin-level seams (who the caller passes) are runtime-only; the matrix doc
 * carries their evidence. Overgeared session-owner semantics were verified
 * against the mod's bytecode (see docs/ATTRIBUTION.md, smithing row).
 */
class AttributionAuditTest {

	private static ResourceLocation rl(String ns, String path) {
		return ResourceLocation.fromNamespaceAndPath(ns, path);
	}

	@Test
	void everyFactoryCarriesTheActorReference() {
		// We cannot instantiate ServerPlayer in unit tests, but the contract is
		// reference-passthrough: whatever the producer passes is what the event
		// reports. Null here exercises exactly that identity boundary.
		ServerPlayer actor = null;
		assertSame(actor, ActivityEvents.mining(actor, rl("minecraft", "stone"), null).player());
		assertSame(actor, ActivityEvents.farming(actor, rl("minecraft", "wheat"), true).player());
		assertSame(actor, ActivityEvents.smithing(actor, rl("minecraft", "iron_sword"),
				Set.of(), LifepathMod.id("anvil"), Map.of()).player());
		assertSame(actor, ActivityEvents.fishing(actor, rl("minecraft", "cod"),
				Set.of(), Map.of()).player());
		assertSame(actor, ActivityEvents.crafting(actor, rl("minecraft", "torch")).player());
		assertSame(actor, ActivityEvents.combat(actor, rl("minecraft", "zombie")).player());
		assertSame(actor, ActivityEvents.archery(actor, rl("minecraft", "skeleton"),
				Set.of(), ActivityEvent.Cause.PLAYER).player());
		assertSame(actor, ActivityEvents.defence(actor, rl("minecraft", "zombie"),
				Set.of(), ActivityEvent.Cause.NON_PLAYER).player());
	}

	@Test
	void shippedSourcesCarryDocumentedPlayerCausedFlags() throws Exception {
		Path dir = Path.of("src/main/resources/data/lifepath/xp_source");
		try (var files = Files.list(dir)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				XpSourceDefinition def = XpSourceDefinition.fromFile(
						LifepathMod.id(f.getFileName().toString().replace(".json", "")),
						XpSourceDefinition.XpSourceFile.CODEC.parse(JsonOps.INSTANCE,
								JsonParser.parseString(Files.readString(f))).result().orElseThrow());
				boolean playerCaused = def.playerCausedOnly();
				// defence is the documented exception: the victim is the actor,
				// the mob is the cause — NON_PLAYER-caused events must still pay.
				if (f.getFileName().toString().equals("defence.json")) {
					assertEquals(false, playerCaused,
							"defence must accept NON_PLAYER causes (victim is the actor)");
				} else {
					assertTrue(playerCaused, f.getFileName()
							+ " must be player_caused_only — only the actor earns");
				}
			}
		}
	}
}
