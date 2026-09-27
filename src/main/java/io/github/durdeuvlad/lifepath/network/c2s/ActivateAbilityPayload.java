package io.github.durdeuvlad.lifepath.network.c2s;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S "activate ability {@code abilityId}" request (M4-1). The client is
 * never trusted: the server re-validates ownership, trigger kind, cooldown,
 * conditions and cost before anything executes — a forged packet is inert.
 *
 * The sentinel {@link #AUTO} asks the server to resolve the request against
 * the player's first owned ACTIVE ability — used when the player presses the
 * key without having picked a specific ability in the character screen.
 */
public record ActivateAbilityPayload(ResourceLocation abilityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ActivateAbilityPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("ability/activate"));
	/** "Pick the first owned ACTIVE ability" — resolved server-side only. */
	public static final ResourceLocation AUTO = LifepathMod.id("ability/auto");
	public static final StreamCodec<RegistryFriendlyByteBuf, ActivateAbilityPayload> PACKET_CODEC =
			ResourceLocation.STREAM_CODEC.map(ActivateAbilityPayload::new, ActivateAbilityPayload::abilityId)
					.cast();

	@Override
	public CustomPacketPayload.Type<ActivateAbilityPayload> type() {
		return ID;
	}
}
