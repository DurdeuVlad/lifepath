package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C "highlight these entities" (M4-3). Sent ONLY to the players who should
 * see the highlight — `private` abilities reach the caster alone (the global
 * glowing flag would leak the highlight to everyone and is never used).
 * {@code entityIds} are network entity ids; the client renders a temporary
 * particle outline until {@code durationTicks} elapses.
 */
public record HighlightEntitiesPayload(List<Integer> entityIds, int durationTicks)
		implements CustomPayload {
	public static final CustomPayload.Id<HighlightEntitiesPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("ability/highlight"));
	public static final PacketCodec<RegistryByteBuf, HighlightEntitiesPayload> PACKET_CODEC =
			PacketCodec.tuple(
					PacketCodecs.INTEGER.collect(PacketCodecs.toList()),
					HighlightEntitiesPayload::entityIds,
					PacketCodecs.INTEGER, HighlightEntitiesPayload::durationTicks,
					HighlightEntitiesPayload::new);

	@Override
	public CustomPayload.Id<HighlightEntitiesPayload> getId() {
		return ID;
	}
}
