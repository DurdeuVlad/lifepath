package io.github.durdeuvlad.lifepath.client;

import io.github.durdeuvlad.lifepath.LifepathMod;
import net.fabricmc.api.ClientModInitializer;

public class LifepathClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LifepathMod.LOGGER.info("Lifepath client initializing (version {})", LifepathMod.modVersion());
	}
}
