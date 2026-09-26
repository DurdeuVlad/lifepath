package io.github.durdeuvlad.lifepath.client;

import io.github.durdeuvlad.lifepath.LifepathMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class LifepathClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LifepathMod.LOGGER.info("Lifepath client initializing (version {})", LifepathMod.modVersion());
	}
}
