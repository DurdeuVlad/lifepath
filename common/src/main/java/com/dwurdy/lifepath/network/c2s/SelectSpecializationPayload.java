package com.dwurdy.lifepath.network.c2s;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S "pick specialization {@code specializationId}" request (M14) — the
 * player path for the one-time first-pick. The server re-validates that the
 * def exists and the player is still unspecialized; re-picks stay an admin
 * {@code /lifepath specialization set} action. A forged packet is inert
 * (denials arrive as a {@code selection_denied} feedback event).
 */
public record SelectSpecializationPayload(ResourceLocation specializationId)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectSpecializationPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("selection/specialization"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpecializationPayload> PACKET_CODEC =
			ResourceLocation.STREAM_CODEC.map(SelectSpecializationPayload::new,
					SelectSpecializationPayload::specializationId).cast();

	@Override
	public CustomPacketPayload.Type<SelectSpecializationPayload> type() {
		return ID;
	}
}
