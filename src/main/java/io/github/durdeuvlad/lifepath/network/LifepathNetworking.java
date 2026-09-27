package io.github.durdeuvlad.lifepath.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Central registration point for all Lifepath play payloads.
 *
 * <p>Convention: payload records live in {@code network/s2c/} (server → client,
 * e.g. state sync for UI/HUD) or {@code network/c2s/} (client → server, e.g.
 * ability-use requests — always re-validated server-side, never trusted).
 * Payload channel identifiers use {@code lifepath:<system>/<name>} via
 * {@link LifepathMod#id}.
 *
 * <p>Type registration ({@link #registerS2C}/{@link #registerC2S}) must happen in
 * the common sourceset so both environments share the codec; receivers are
 * environment-specific:
 * C2S handlers register here (server), S2C handlers register in
 * {@code client/network/ClientLifepathNetworking} via {@code ClientPlayNetworking}.
 *
 * <p>Server-authority rule: C2S payloads are requests; the server re-checks
 * permissions, cooldowns and state before acting.
 */
public final class LifepathNetworking {
	private LifepathNetworking() {
	}

	/** Builds the typed channel id used by payload records' {@code ID} constants. */
	public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadId(ResourceLocation channel) {
		return new CustomPacketPayload.Type<>(channel);
	}

	/** Registers a server → client payload type. Call during common mod init. */
	public static <T extends CustomPacketPayload> void registerS2C(
			CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.playS2C().register(id, codec);
	}

	/** Registers a client → server payload type. Call during common mod init. */
	public static <T extends CustomPacketPayload> void registerC2S(
			CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.playC2S().register(id, codec);
	}

	/**
	 * Registers the server-side receiver for a C2S payload type. Common code only.
	 *
	 * @throws IllegalArgumentException if the payload type was not registered via
	 *         {@link #registerC2S} first
	 * @throws IllegalStateException    if a receiver is already registered for the type
	 *         (duplicate registration is a bug — the first would silently win)
	 */
	public static <T extends CustomPacketPayload> void onC2S(
			CustomPacketPayload.Type<T> id, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
		if (!ServerPlayNetworking.registerGlobalReceiver(id, handler)) {
			throw new IllegalStateException("duplicate C2S receiver for payload " + id.id());
		}
	}
}
