package com.dwurdy.lifepath.network.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dwurdy.lifepath.network.s2c.SelectionCatalogPayload.Entry;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * M14: the catalog payload's nested entry codec must round-trip byte-exactly
 * — a field-order slip would mislabel every picker card downstream.
 */
class SelectionCatalogPayloadTest {

	@BeforeAll
	static void bootMinecraft() {
		// ComponentSerialization's codec builds on vanilla registries —
		// the headless suite never boots Minecraft otherwise.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void packetCodecRoundTripsFullPayload() {
		SelectionCatalogPayload p = new SelectionCatalogPayload(
				List.of(
						new Entry("lifepath:human", Component.literal("Human"),
								Component.literal("Adaptable."),
								"lifepath:textures/gui/species/human.png",
								List.of(Component.translatable(
												"text.lifepath.detail.active", "Adrenaline"),
										Component.literal("Versatile")),
								Entry.AVAILABLE),
						new Entry("lifepath:phantom", Component.literal("Phantom"),
								Component.literal("Half-ghost."),
								"", List.of(), Entry.NEEDS_UNLOCK)),
				List.of(new Entry("lifepath:smith", Component.literal("Smith"),
								Component.empty(),
						"lifepath:textures/gui/specialization/smith.png",
						List.of(Component.translatable(
								"text.lifepath.detail.skill_start_apt",
								"smithing", 20, "A")),
						Entry.AVAILABLE)),
				List.of(new Entry("lifepath:fox", Component.literal("Fox"),
								Component.literal("Sneaky."),
						"lifepath:textures/gui/morph/fox.png",
						List.of(Component.literal("Max Health: 10")),
						Entry.AVAILABLE)),
				java.util.Set.of("lifepath:anima"));

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		SelectionCatalogPayload.PACKET_CODEC.encode(buf, p);
		SelectionCatalogPayload d = SelectionCatalogPayload.PACKET_CODEC.decode(buf);

		assertEquals(p, d);
		assertEquals(2, d.species().size());
		assertEquals(Entry.NEEDS_UNLOCK, d.species().get(1).availability());
		assertEquals(1, d.morphForms().size());
		assertEquals("lifepath:fox", d.morphForms().get(0).id());
		assertEquals(java.util.Set.of("lifepath:anima"), d.morphSpecies());
	}

	@Test
	void emptyPayloadRoundTrips() {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		SelectionCatalogPayload p = SelectionCatalogPayload.empty();
		SelectionCatalogPayload.PACKET_CODEC.encode(buf, p);
		SelectionCatalogPayload d = SelectionCatalogPayload.PACKET_CODEC.decode(buf);
		assertEquals(p, d);
	}
}
