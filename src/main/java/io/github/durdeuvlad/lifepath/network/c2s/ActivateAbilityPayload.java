package io.github.durdeuvlad.lifepath.network.c2s;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S "activate ability {@code abilityId}" request (M4-1). The client is
 * never trusted: the server re-validates ownership, trigger kind, cooldown,
 * conditions and cost before anything executes — a forged packet is inert.
 */
public record ActivateAbilityPayload(Identifier abilityId) implements CustomPayload {
	public static final CustomPayload.Id<ActivateAbilityPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("ability/activate"));
	public static final PacketCodec<RegistryByteBuf, ActivateAbilityPayload> PACKET_CODEC =
			Identifier.PACKET_CODEC.xmap(ActivateAbilityPayload::new, ActivateAbilityPayload::abilityId)
					.cast();

	@Override
	public CustomPayload.Id<ActivateAbilityPayload> getId() {
		return ID;
	}
}
