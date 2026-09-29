package io.github.durdeuvlad.lifepath.network.c2s;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S "send me the selection catalog" refresh request (M14). The selection
 * screen sends this when it opens so the option list is resolved against
 * current registries, unlocks and choice state — the join-time push can be
 * stale after a datapack reload or a mid-session unlock. Carries no data;
 * the answer is a fresh {@code SelectionCatalogPayload}.
 */
public record RequestSelectionCatalogPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RequestSelectionCatalogPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("selection/catalog_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RequestSelectionCatalogPayload> PACKET_CODEC =
			StreamCodec.unit(new RequestSelectionCatalogPayload());

	@Override
	public CustomPacketPayload.Type<RequestSelectionCatalogPayload> type() {
		return ID;
	}
}
