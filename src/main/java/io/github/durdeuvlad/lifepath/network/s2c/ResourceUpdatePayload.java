package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Single-resource delta sent server → client when a resource's value changes
 * (M4-4/M4-5): {@code (resourceId, current, min, max, bandIndex)}. The M1-2
 * snapshot carries the full map on join/respawn/mutation; this keeps a HUD
 * meter fresh between snapshots — regen sweeps would otherwise leave the
 * display a snapshot-cycle stale. {@code bandIndex} is the
 * {@code ResourceDefinition} band containing {@code current} (−1 = none) so
 * M6 feedback never needs the server-side definition.
 *
 * <p>Advisory only — the server owns resource mutation; there is no C2S path.
 */
public record ResourceUpdatePayload(Identifier resourceId, double current,
		double min, double max, int bandIndex) implements CustomPayload {
	public static final CustomPayload.Id<ResourceUpdatePayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/resource"));

	public static final PacketCodec<RegistryByteBuf, ResourceUpdatePayload> PACKET_CODEC =
			PacketCodec.tuple(
					Identifier.PACKET_CODEC, ResourceUpdatePayload::resourceId,
					PacketCodecs.DOUBLE, ResourceUpdatePayload::current,
					PacketCodecs.DOUBLE, ResourceUpdatePayload::min,
					PacketCodecs.DOUBLE, ResourceUpdatePayload::max,
					PacketCodecs.INTEGER, ResourceUpdatePayload::bandIndex,
					ResourceUpdatePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
