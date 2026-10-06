package com.dwurdy.lifepath.network.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dwurdy.lifepath.network.s2c.IdentitySummaryPayload.Entry;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Component;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;

/**
 * M12-2: the payload's hand-written {@code IdentityCore} codec (past
 * {@code PacketCodec.tuple} arity) and the {@link Entry}-carrying maps must
 * round-trip byte-exactly — a field-order slip would corrupt every screen
 * downstream.
 */
class IdentitySummaryPayloadTest {

	@BeforeAll
	static void bootMinecraft() {
		// ComponentSerialization's codec builds on vanilla registries —
		// the headless suite never boots Minecraft otherwise.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void packetCodecRoundTripsFullPayload() {
		IdentitySummaryPayload p = new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore(
						"lifepath:sylvian", Component.literal("Sylvian"),
						Component.literal("Desc here"),
						"lifepath:textures/gui/species/sylvian.png",
						"lifepath:miner", Component.literal("Miner"),
						"lifepath:textures/gui/spec/miner.png",
						List.of(Component.literal("+ Sturdy"),
								Component.literal("- Slow"))),
				List.of(new Entry("lifepath:mining", Component.literal("Mining"),
						"lifepath:textures/gui/skill/mining.png")),
				Map.of("conditions", List.of(
						new Entry("lifepath:chilled", Component.literal("Chilled"),
								"lifepath:textures/gui/condition/chilled.png")),
						"traits", List.of(
								new Entry("lifepath:night_eyes", Component.literal("Night Eyes"), ""))),
				// Two entries so iteration order is observable — the decode
				// must preserve the sender's order (LinkedHashMap), since the
				// character screen iterates the values directly.
				Map.of("lifepath:frost_nova",
						new IdentitySummaryPayload.AbilityEntry(
								"lifepath:frost_nova", Component.literal("Frost Nova"),
								"lifepath:textures/gui/ability/frost.png",
								true, Component.empty()),
						"lifepath:zz_passive",
						new IdentitySummaryPayload.AbilityEntry(
								"lifepath:zz_passive", Component.literal("Passive"),
								"", false, Component.empty())),
				List.of(new IdentitySummaryPayload.ResourceDisplay(
						"lifepath:temperature", Component.literal("Temperature"), 50, 1,
						List.of(Component.literal("Cold"), Component.literal("Hot")),
						"lifepath:textures/gui/resource/temp.png")),
				new IdentitySummaryPayload.MorphView("lifepath:fox",
						"minecraft:fox", Component.literal("Fox"),
						"lifepath:textures/gui/morph/fox.png", true));

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		IdentitySummaryPayload.PACKET_CODEC.encode(buf, p);
		IdentitySummaryPayload d = IdentitySummaryPayload.PACKET_CODEC.decode(buf);

		assertEquals(p.identity(), d.identity());
		assertEquals(p.specFocus(), d.specFocus());
		assertEquals(p.sections(), d.sections());
		assertEquals(p.abilities(), d.abilities());
		// Map equality ignores order — assert the wire order explicitly.
		assertEquals(List.copyOf(p.abilities().keySet()),
				List.copyOf(d.abilities().keySet()));
		assertEquals(p.resourceDisplays(), d.resourceDisplays());
		assertEquals(p.morph(), d.morph());
	}

	@Test
	void packetCodecRoundTripsTranslatableWithFallback() {
		// The real wire shape: translatableWithFallback names plus
		// translatable detail args mixing components and primitives.
		IdentitySummaryPayload p = new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore(
						"lifepath:automaton",
						Component.translatableWithFallback(
								"lifepath.species.automaton.name", "Automaton"),
						Component.translatableWithFallback(
								"lifepath.species.automaton.description", "Desc"),
						"",
						"lifepath:miner",
						Component.translatableWithFallback(
								"lifepath.specialization.miner.name", "Miner"),
						"", List.of()),
				List.of(new Entry("lifepath:smithing",
						Component.translatableWithFallback(
								"lifepath.skill.smithing.name", "Smithing"),
						"")),
				Map.of("traits", List.of(new Entry("lifepath:x",
						Component.translatable("text.lifepath.detail.skill_start_apt",
								Component.translatableWithFallback(
										"lifepath.skill.smithing.name", "Smithing"),
								20, "A"),
						""))),
				Map.of(), List.of(), IdentitySummaryPayload.MorphView.EMPTY);
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(
				io.netty.buffer.Unpooled.buffer(),
				net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
						net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
		IdentitySummaryPayload.PACKET_CODEC.encode(buf, p);
		IdentitySummaryPayload d = IdentitySummaryPayload.PACKET_CODEC.decode(buf);
		assertEquals(p, d);
	}

	@Test
	void builtPayloadRoundTrips() throws Exception {
		// Round-trips the REAL build() output — keyedText components with
		// fallbacks, populated sections, ability map, resource displays.
		var data = com.dwurdy.lifepath.character.PlayerCharacterData
				.createDefault();
		data.setSpeciesId(com.dwurdy.lifepath.LifepathMod.id("automaton"));
		data.setSpecializationId(com.dwurdy.lifepath.LifepathMod.id("miner"));
		IdentitySummaryPayload p = com.dwurdy.lifepath.character.IdentitySummary.build(data);
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(
				io.netty.buffer.Unpooled.buffer(),
				net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
						net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
		IdentitySummaryPayload.PACKET_CODEC.encode(buf, p);
		IdentitySummaryPayload d = IdentitySummaryPayload.PACKET_CODEC.decode(buf);
		assertEquals(p.identity(), d.identity());
		assertEquals(p.specFocus(), d.specFocus());
		assertEquals(p.sections(), d.sections());
	}

	@Test
	void emptyPayloadRoundTrips() {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		IdentitySummaryPayload p = IdentitySummaryPayload.empty();
		IdentitySummaryPayload.PACKET_CODEC.encode(buf, p);
		IdentitySummaryPayload d = IdentitySummaryPayload.PACKET_CODEC.decode(buf);
		assertEquals(p.identity(), d.identity());
		assertEquals(p.sections(), d.sections());
	}
}
