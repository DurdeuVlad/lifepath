package io.github.durdeuvlad.lifepath.network.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.Entry;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * M14: the catalog payload's nested entry codec must round-trip byte-exactly
 * — a field-order slip would mislabel every picker card downstream.
 */
class SelectionCatalogPayloadTest {

	@Test
	void packetCodecRoundTripsFullPayload() {
		SelectionCatalogPayload p = new SelectionCatalogPayload(
				List.of(
						new Entry("lifepath:human", "Human", "Adaptable.",
								"lifepath:textures/gui/species/human.png",
								List.of("Active: Adrenaline", "Versatile"),
								Entry.AVAILABLE),
						new Entry("lifepath:phantom", "Phantom", "Half-ghost.",
								"", List.of(), Entry.NEEDS_UNLOCK)),
				List.of(new Entry("lifepath:smith", "Smith", "",
						"lifepath:textures/gui/specialization/smith.png",
						List.of("smithing starts at Lv20 · Apt A"),
						Entry.AVAILABLE)));

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		SelectionCatalogPayload.PACKET_CODEC.encode(buf, p);
		SelectionCatalogPayload d = SelectionCatalogPayload.PACKET_CODEC.decode(buf);

		assertEquals(p, d);
		assertEquals(2, d.species().size());
		assertEquals(Entry.NEEDS_UNLOCK, d.species().get(1).availability());
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
