package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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
public record ResourceUpdatePayload(ResourceLocation resourceId, double current,
		double min, double max, int bandIndex) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ResourceUpdatePayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/resource"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ResourceUpdatePayload> PACKET_CODEC =
			StreamCodec.composite(
					ResourceLocation.STREAM_CODEC, ResourceUpdatePayload::resourceId,
					ByteBufCodecs.DOUBLE, ResourceUpdatePayload::current,
					ByteBufCodecs.DOUBLE, ResourceUpdatePayload::min,
					ByteBufCodecs.DOUBLE, ResourceUpdatePayload::max,
					ByteBufCodecs.INT, ResourceUpdatePayload::bandIndex,
					ResourceUpdatePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
