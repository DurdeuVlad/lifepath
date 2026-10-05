package com.dwurdy.lifepath.neoforge;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.client.platform.ClientPlatformServices;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge adapter for the client platform seam (M13-4). Only constructed
 * when {@code FMLEnvironment.dist == Dist.CLIENT} — never class-loads on a
 * dedicated server.
 */
public final class NeoforgeClientPlatform implements ClientPlatformServices {

	private final NeoforgePlatform platform;

	public NeoforgeClientPlatform(IEventBus modBus, NeoforgePlatform platform) {
		this.platform = platform;
		modBus.addListener(RegisterKeyMappingsEvent.class,
				event -> pendingKeyMappings.forEach(event::register));
		modBus.addListener(RegisterGuiLayersEvent.class,
				event -> pendingHud.forEach(listener ->
						event.registerAboveAll(LifepathMod.id("hud"), listener::render)));
		modBus.addListener(RegisterClientReloadListenersEvent.class,
				event -> pendingClientReload.forEach((id, apply) ->
						event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
							@Override
							protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
								return null;
							}

							@Override
							protected void apply(Void prepared, ResourceManager manager,
									ProfilerFiller profiler) {
								apply.accept(manager);
							}
						})));
	}

	private final java.util.List<KeyMapping> pendingKeyMappings = new java.util.ArrayList<>();
	private final java.util.List<HudRenderListener> pendingHud = new java.util.ArrayList<>();
	private final java.util.Map<ResourceLocation, Consumer<ResourceManager>> pendingClientReload =
			new java.util.LinkedHashMap<>();

	@Override
	public KeyMapping registerKeyMapping(KeyMapping mapping) {
		// Queued — NeoForge registers key mappings during
		// RegisterKeyMappingsEvent, fired after mod construction.
		pendingKeyMappings.add(mapping);
		return mapping;
	}

	@Override
	public void onEndClientTick(Consumer<Minecraft> listener) {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
				event -> listener.accept(Minecraft.getInstance()));
	}

	@Override
	public void onClientJoin(Runnable listener) {
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
				event -> listener.run());
	}

	@Override
	public void onClientDisconnect(Runnable listener) {
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
				event -> listener.run());
	}

	@Override
	public void onHudRender(HudRenderListener listener) {
		pendingHud.add(listener);
	}

	@Override
	public <T extends CustomPacketPayload> void onS2C(
			CustomPacketPayload.Type<T> id, BiConsumer<T, Minecraft> handler) {
		platform.queueClientS2CHandler(id, handler);
	}

	@Override
	public void sendToServer(CustomPacketPayload payload) {
		PacketDistributor.sendToServer(payload);
	}

	@Override
	public void registerClientReloadListener(ResourceLocation id,
			Consumer<ResourceManager> apply) {
		pendingClientReload.put(id, apply);
	}
}
