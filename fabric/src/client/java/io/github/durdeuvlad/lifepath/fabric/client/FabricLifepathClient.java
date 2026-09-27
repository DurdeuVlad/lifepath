package io.github.durdeuvlad.lifepath.fabric.client;

import io.github.durdeuvlad.lifepath.client.LifepathClient;
import io.github.durdeuvlad.lifepath.client.platform.ClientPlatform;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric client entrypoint (M13-3): installs the client platform adapter,
 * then delegates to {@link LifepathClient#init()}.
 */
public class FabricLifepathClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlatform.init(new FabricClientPlatform());
		LifepathClient.init();
	}
}
