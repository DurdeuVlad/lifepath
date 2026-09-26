package io.github.durdeuvlad.lifepath.client;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.network.ClientLifepathNetworking;
import io.github.durdeuvlad.lifepath.network.s2c.CharacterSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

@Environment(EnvType.CLIENT)
public class LifepathClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LifepathMod.LOGGER.info("Lifepath client initializing (version {})", LifepathMod.modVersion());

		ClientLifepathNetworking.onS2C(CharacterSyncPayload.ID, (payload, context) ->
				context.client().execute(() -> ClientCharacterState.apply(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientCharacterState.clear());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientCharacterState.clear());
	}
}
