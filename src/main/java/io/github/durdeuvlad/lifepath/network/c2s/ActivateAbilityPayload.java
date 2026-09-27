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
 *
 * The sentinel {@link #AUTO} asks the server to resolve the request against
 * the player's first owned ACTIVE ability — used when the player presses the
 * key without having picked a specific ability in the character screen.
 */
public record ActivateAbilityPayload(Identifier abilityId) implements CustomPayload {
	public static final CustomPayload.Id<ActivateAbilityPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("ability/activate"));
	/** "Pick the first owned ACTIVE ability" — resolved server-side only. */
	public static final Identifier AUTO = LifepathMod.id("ability/auto");
	public static final PacketCodec<RegistryByteBuf, ActivateAbilityPayload> PACKET_CODEC =
			Identifier.PACKET_CODEC.xmap(ActivateAbilityPayload::new, ActivateAbilityPayload::abilityId)
					.cast();

	@Override
	public CustomPayload.Id<ActivateAbilityPayload> getId() {
		return ID;
	}
}
