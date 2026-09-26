package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Single-cooldown delta sent server → client when a cooldown is triggered or
 * cleared (M4-4): {@code (abilityId, expiryEpochMs)}, where {@code expiry <= 0}
 * means "cleared". The M1-2 snapshot still carries the full active-cooldown
 * map on join/respawn/mutation; this packet keeps HUD state fresh between
 * snapshots without re-sending the whole character model.
 *
 * <p>Advisory only — the server re-validates cooldowns on every ability eval.
 */
public record CooldownUpdatePayload(Identifier abilityId, long expiryEpochMs)
		implements CustomPayload {
	public static final CustomPayload.Id<CooldownUpdatePayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/cooldown"));

	public static final PacketCodec<RegistryByteBuf, CooldownUpdatePayload> PACKET_CODEC =
			PacketCodec.tuple(
					Identifier.PACKET_CODEC, CooldownUpdatePayload::abilityId,
					PacketCodecs.VAR_LONG, CooldownUpdatePayload::expiryEpochMs,
					CooldownUpdatePayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
