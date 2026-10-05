package com.dwurdy.lifepath.network.c2s;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S "pick morph form {@code formId}" request (M-2) — sent by the
 * selection screen's second step after a morph-capable species pick. The
 * client is never trusted: the server re-validates the form def, that the
 * character's species actually carries a {@code morph_toggle} ability, and
 * the one-time pick rule before writing; a forged packet is inert (denials
 * arrive as a {@code selection_denied} feedback event).
 */
public record SelectMorphFormPayload(ResourceLocation formId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectMorphFormPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("selection/morph_form"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectMorphFormPayload> PACKET_CODEC =
			ResourceLocation.STREAM_CODEC.map(SelectMorphFormPayload::new,
					SelectMorphFormPayload::formId).cast();

	@Override
	public CustomPacketPayload.Type<SelectMorphFormPayload> type() {
		return ID;
	}
}
