package io.github.durdeuvlad.lifepath.client.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-side counterpart of {@code LifepathNetworking}: registers receivers for
 * S2C payload types (state sync for UI/HUD). C2S sends use
 * {@code ClientPlayNetworking.send(payload)} at call sites.
 *
 * <p>Lives in the client sourceset so the dedicated server never class-loads
 * {@code ClientPlayNetworking}.
 */
@Environment(EnvType.CLIENT)
public final class ClientLifepathNetworking {
	private ClientLifepathNetworking() {
	}

	/**
	 * Registers the client-side receiver for an S2C payload type.
	 *
	 * @throws IllegalStateException if a receiver is already registered for the
	 *         type (duplicate registration is a bug — the first would silently win)
	 */
	public static <T extends CustomPacketPayload> void onS2C(
			CustomPacketPayload.Type<T> id, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
		if (!ClientPlayNetworking.registerGlobalReceiver(id, handler)) {
			throw new IllegalStateException("duplicate S2C receiver for payload " + id.id());
		}
	}
}
