package com.dwurdy.lifepath.network.s2c;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.network.LifepathNetworking;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C "highlight these entities" (M4-3). Sent ONLY to the players who should
 * see the highlight — `private` abilities reach the caster alone (the global
 * glowing flag would leak the highlight to everyone and is never used).
 * {@code entityIds} are network entity ids; the client renders a temporary
 * particle outline until {@code durationTicks} elapses.
 */
public record HighlightEntitiesPayload(List<Integer> entityIds, int durationTicks)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HighlightEntitiesPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("ability/highlight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HighlightEntitiesPayload> PACKET_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.INT.apply(ByteBufCodecs.list()),
					HighlightEntitiesPayload::entityIds,
					ByteBufCodecs.INT, HighlightEntitiesPayload::durationTicks,
					HighlightEntitiesPayload::new);

	@Override
	public CustomPacketPayload.Type<HighlightEntitiesPayload> type() {
		return ID;
	}
}
