package com.dwurdy.lifepath.fabric;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.Platform;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint (M13-3): installs the platform adapter, then delegates to
 * the loader-agnostic {@link LifepathMod#init()}. All gameplay wiring lives in
 * common; this class is deliberately two lines.
 */
public class FabricLifepath implements ModInitializer {
	@Override
	public void onInitialize() {
		Platform.init(new FabricPlatform());
		LifepathMod.init();
	}
}
