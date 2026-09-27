package io.github.durdeuvlad.lifepath.client.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Client-side port (M13-3): keybinding registration, client tick, HUD layer,
 * client connection lifecycle, client resource reload and S2C/C2S channel
 * ends. {@code ClientLifepathClient} in each loader module implements this.
 */
public interface ClientPlatformServices {

	/** Registers a keybinding with the loader's client key registry; returns the registered mapping. */
	KeyMapping registerKeyMapping(KeyMapping mapping);

	void onEndClientTick(Consumer<Minecraft> listener);

	/** Fires when the client joins a server session. */
	void onClientJoin(Runnable listener);

	/** Fires when the client leaves a server session. */
	void onClientDisconnect(Runnable listener);

	/** Registers an in-game HUD overlay renderer. */
	void onHudRender(HudRenderListener listener);

	/**
	 * Registers the client-side receiver for an S2C type. The handler gets the
	 * payload plus the client instance; callers schedule with
	 * {@code client.execute(...)}.
	 *
	 * @throws IllegalStateException if a receiver is already registered for the type
	 */
	<T extends CustomPacketPayload> void onS2C(
			CustomPacketPayload.Type<T> id, BiConsumer<T, Minecraft> handler);

	/**
	 * Sends a C2S payload to the connected server.
	 *
	 * @throws IllegalStateException when the client isn't in a game
	 */
	void sendToServer(CustomPacketPayload payload);

	/** Client-pack (resource pack) reload hook — textures, models, etc. */
	void registerClientReloadListener(ResourceLocation id, Consumer<ResourceManager> apply);

	@FunctionalInterface
	interface HudRenderListener {
		void render(GuiGraphics graphics, DeltaTracker delta);
	}
}
