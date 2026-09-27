package io.github.durdeuvlad.lifepath.network;

import io.github.durdeuvlad.lifepath.platform.Platform;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central registration point for all Lifepath play payloads.
 *
 * <p>Convention: payload records live in {@code network/s2c/} (server → client,
 * e.g. state sync for UI/HUD) or {@code network/c2s/} (client → server, e.g.
 * ability-use requests — always re-validated server-side, never trusted).
 * Payload channel identifiers use {@code lifepath:<system>/<name>} via
 * {@link io.github.durdeuvlad.lifepath.LifepathMod#id}.
 *
 * <p>Type registration ({@link #registerS2C}/{@link #registerC2S}) must happen in
 * the common sourceset so both environments share the codec; receivers are
 * environment-specific:
 * C2S handlers register here (server), S2C handlers register in
 * {@code client/network/ClientLifepathNetworking}.
 *
 * <p>Server-authority rule: C2S payloads are requests; the server re-checks
 * permissions, cooldowns and state before acting.
 *
 * <p>M13-3: this class is a thin facade over {@link Platform} — loaders supply
 * the actual channel plumbing.
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
		Platform.get().registerS2C(id, codec);
	}

	/** Registers a client → server payload type. Call during common mod init. */
	public static <T extends CustomPacketPayload> void registerC2S(
			CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		Platform.get().registerC2S(id, codec);
	}

	/**
	 * Registers the server-side receiver for a C2S payload type. Common code only.
	 *
	 * @throws IllegalStateException if a receiver is already registered for the type
	 *         (duplicate registration is a bug — the first would silently win)
	 */
	public static <T extends CustomPacketPayload> void onC2S(
			CustomPacketPayload.Type<T> id, BiConsumer<T, ServerPlayer> handler) {
		Platform.get().onC2S(id, handler);
	}

	/** Sends an S2C payload to one player (server-side only). */
	public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
		Platform.get().sendTo(player, payload);
	}
}
