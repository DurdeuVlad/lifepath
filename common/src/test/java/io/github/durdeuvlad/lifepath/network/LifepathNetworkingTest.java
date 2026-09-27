package io.github.durdeuvlad.lifepath.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.durdeuvlad.lifepath.LifepathMod;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

class LifepathNetworkingTest {

	@Test
	void payloadIdWrapsChannelIdentifier() {
		CustomPacketPayload.Type<CustomPacketPayload> id = LifepathNetworking.payloadId(LifepathMod.id("test/dummy"));
		assertEquals(LifepathMod.id("test/dummy"), id.id());
	}
}
