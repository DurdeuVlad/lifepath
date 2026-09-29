package io.github.durdeuvlad.lifepath.network.c2s;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S "pick species {@code speciesId}" request (M14) — sent by the selection
 * screen's confirm action. The client is never trusted: the server
 * re-validates existence, {@code selection} policy and held unlocks before
 * writing; a forged packet is inert (denials arrive as a
 * {@code selection_denied} feedback event).
 */
public record SelectSpeciesPayload(ResourceLocation speciesId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectSpeciesPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("selection/species"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpeciesPayload> PACKET_CODEC =
			ResourceLocation.STREAM_CODEC.map(SelectSpeciesPayload::new,
					SelectSpeciesPayload::speciesId).cast();

	@Override
	public CustomPacketPayload.Type<SelectSpeciesPayload> type() {
		return ID;
	}
}
