package io.github.durdeuvlad.lifepath.client.network;

import io.github.durdeuvlad.lifepath.client.platform.ClientPlatform;
import io.github.durdeuvlad.lifepath.platform.ClientOnly;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-side counterpart of {@code LifepathNetworking}: registers receivers
 * for S2C payload types (state sync for UI/HUD). C2S sends go through
 * {@code ClientPlatform.get().sendToServer(payload)} at call sites.
 *
 * <p>M13-3: thin facade over {@link ClientPlatform} — loaders supply the
 * channel plumbing. Lives in the client sourceset so the dedicated server
 * never class-loads it.
 */
@ClientOnly
public final class ClientLifepathNetworking {
	private ClientLifepathNetworking() {
	}

	/**
	 * Registers the client-side receiver for an S2C payload type. The handler
	 * receives the payload plus the client instance — schedule work with
	 * {@code client.execute(...)}.
	 *
	 * @throws IllegalStateException if a receiver is already registered for the
	 *         type (duplicate registration is a bug — the first would silently win)
	 */
	public static <T extends CustomPacketPayload> void onS2C(
			CustomPacketPayload.Type<T> id, BiConsumer<T, Minecraft> handler) {
		ClientPlatform.get().onS2C(id, handler);
	}
}
