package io.github.durdeuvlad.lifepath.network.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload.Entry;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

/**
 * M12-2: the payload's hand-written {@code IdentityCore} codec (past
 * {@code PacketCodec.tuple} arity) and the {@link Entry}-carrying maps must
 * round-trip byte-exactly — a field-order slip would corrupt every screen
 * downstream.
 */
class IdentitySummaryPayloadTest {

	@Test
	void packetCodecRoundTripsFullPayload() {
		IdentitySummaryPayload p = new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore(
						"lifepath:sylvian", "Sylvian", "Desc here",
						"lifepath:textures/gui/species/sylvian.png",
						"lifepath:miner", "Miner",
						"lifepath:textures/gui/spec/miner.png"),
				List.of(new Entry("lifepath:mining", "Mining",
						"lifepath:textures/gui/skill/mining.png")),
				Map.of("conditions", List.of(
						new Entry("lifepath:chilled", "Chilled",
								"lifepath:textures/gui/condition/chilled.png")),
						"traits", List.of(
								new Entry("lifepath:night_eyes", "Night Eyes", ""))),
				Map.of("lifepath:frost_nova", new Entry("lifepath:frost_nova",
						"Frost Nova", "lifepath:textures/gui/ability/frost.png")),
				List.of(new IdentitySummaryPayload.ResourceDisplay(
						"lifepath:temperature", "Temperature", 50, 1,
						List.of("Cold", "Hot"))));

		RegistryByteBuf buf = new RegistryByteBuf(
				Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		IdentitySummaryPayload.PACKET_CODEC.encode(buf, p);
		IdentitySummaryPayload d = IdentitySummaryPayload.PACKET_CODEC.decode(buf);

		assertEquals(p.identity(), d.identity());
		assertEquals(p.specFocus(), d.specFocus());
		assertEquals(p.sections(), d.sections());
		assertEquals(p.abilities(), d.abilities());
		assertEquals(p.resourceDisplays(), d.resourceDisplays());
	}

	@Test
	void emptyPayloadRoundTrips() {
		RegistryByteBuf buf = new RegistryByteBuf(
				Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		IdentitySummaryPayload p = IdentitySummaryPayload.empty();
		IdentitySummaryPayload.PACKET_CODEC.encode(buf, p);
		IdentitySummaryPayload d = IdentitySummaryPayload.PACKET_CODEC.decode(buf);
		assertEquals(p.identity(), d.identity());
		assertEquals(p.sections(), d.sections());
	}
}
