package com.dwurdy.lifepath.fabric.client;

import com.dwurdy.lifepath.client.platform.ClientPlatformServices;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Fabric adapter for the client platform seam (M13-3). Installed by
 * {@code FabricLifepathClient#onInitializeClient} before
 * {@code LifepathClient.init()}.
 */
public final class FabricClientPlatform implements ClientPlatformServices {

	@Override
	public KeyMapping registerKeyMapping(KeyMapping mapping) {
		return KeyBindingHelper.registerKeyBinding(mapping);
	}

	@Override
	public void onEndClientTick(Consumer<Minecraft> listener) {
		ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
	}

	@Override
	public void onClientJoin(Runnable listener) {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.run());
	}

	@Override
	public void onClientDisconnect(Runnable listener) {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
	}

	@Override
	public void onHudRender(HudRenderListener listener) {
		HudRenderCallback.EVENT.register(listener::render);
	}

	@Override
	public <T extends CustomPacketPayload> void onS2C(
			CustomPacketPayload.Type<T> id, BiConsumer<T, Minecraft> handler) {
		if (!ClientPlayNetworking.registerGlobalReceiver(id,
				(payload, context) -> handler.accept(payload, context.client()))) {
			throw new IllegalStateException("duplicate S2C receiver for payload " + id.id());
		}
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}

	@Override
	public void registerClientReloadListener(ResourceLocation id,
			Consumer<ResourceManager> apply) {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public ResourceLocation getFabricId() {
						return id;
					}

					@Override
					public void onResourceManagerReload(ResourceManager manager) {
						apply.accept(manager);
					}
				});
	}
}
