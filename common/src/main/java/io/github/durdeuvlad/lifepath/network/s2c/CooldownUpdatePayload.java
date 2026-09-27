package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Single-cooldown delta sent server → client when a cooldown is triggered or
 * cleared (M4-4): {@code (abilityId, expiryEpochMs)}, where {@code expiry <= 0}
 * means "cleared". The M1-2 snapshot still carries the full active-cooldown
 * map on join/respawn/mutation; this packet keeps HUD state fresh between
 * snapshots without re-sending the whole character model.
 *
 * <p>Advisory only — the server re-validates cooldowns on every ability eval.
 */
public record CooldownUpdatePayload(ResourceLocation abilityId, long expiryEpochMs)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CooldownUpdatePayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/cooldown"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CooldownUpdatePayload> PACKET_CODEC =
			StreamCodec.composite(
					ResourceLocation.STREAM_CODEC, CooldownUpdatePayload::abilityId,
					ByteBufCodecs.VAR_LONG, CooldownUpdatePayload::expiryEpochMs,
					CooldownUpdatePayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
