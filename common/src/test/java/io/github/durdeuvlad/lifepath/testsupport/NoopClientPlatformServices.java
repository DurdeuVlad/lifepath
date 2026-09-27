package io.github.durdeuvlad.lifepath.testsupport;

import io.github.durdeuvlad.lifepath.client.platform.ClientPlatformServices;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Headless client platform stub (M13-3): receivers record (keeping the
 * duplicate-registration guard), everything else no-ops.
 */
public final class NoopClientPlatformServices implements ClientPlatformServices {

	private final Set<CustomPacketPayload.Type<?>> s2cReceivers = new CopyOnWriteArraySet<>();

	@Override
	public KeyMapping registerKeyMapping(KeyMapping mapping) {
		return mapping;
	}

	@Override
	public void onEndClientTick(Consumer<Minecraft> listener) {
	}

	@Override
	public void onClientJoin(Runnable listener) {
	}

	@Override
	public void onClientDisconnect(Runnable listener) {
	}

	@Override
	public void onHudRender(HudRenderListener listener) {
	}

	@Override
	public <T extends CustomPacketPayload> void onS2C(
			CustomPacketPayload.Type<T> id, BiConsumer<T, Minecraft> handler) {
		if (!s2cReceivers.add(id)) {
			throw new IllegalStateException("duplicate S2C receiver for payload " + id.id());
		}
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
	}

	@Override
	public void registerClientReloadListener(ResourceLocation id,
			Consumer<ResourceManager> apply) {
	}
}
