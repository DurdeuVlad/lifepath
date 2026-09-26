package io.github.durdeuvlad.lifepath.client;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.network.ClientLifepathNetworking;
import io.github.durdeuvlad.lifepath.network.s2c.CharacterSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import io.github.durdeuvlad.lifepath.client.ability.ClientAbilityState;
import io.github.durdeuvlad.lifepath.client.ability.ClientHighlights;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class LifepathClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LifepathMod.LOGGER.info("Lifepath client initializing (version {})", LifepathMod.modVersion());

		ClientLifepathNetworking.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.HighlightEntitiesPayload.ID,
				(payload, context) -> context.client().execute(() ->
						ClientHighlights.add(payload.entityIds(), payload.durationTicks())));
		ClientLifepathNetworking.onS2C(CharacterSyncPayload.ID, (payload, context) ->
				context.client().execute(() -> ClientCharacterState.apply(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientCharacterState.clear();
			ClientAbilityState.clear();
			ClientHighlights.clear();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientCharacterState.clear());

		// M4-1: rebindable ability key (default G) — sends a C2S activation
		// request for the client-selected ability; the server validates all.
		KeyBinding abilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.lifepath.ability", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
				"key.categories.lifepath"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (abilityKey.wasPressed()) {
				ClientAbilityState.requestActivation();
			}
			ClientHighlights.tick(client);
		});
	}
}
