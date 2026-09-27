package io.github.durdeuvlad.lifepath.neoforge;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.client.platform.ClientPlatform;
import io.github.durdeuvlad.lifepath.platform.Platform;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * NeoForge entrypoint (M13-4): installs the platform adapters, then delegates
 * to the loader-agnostic {@link LifepathMod#init()} (and
 * {@code LifepathClient.init()} on the client dist). The client platform's
 * mod-bus listeners are registered inside the dist check so a dedicated
 * server never class-loads them.
 */
@Mod(LifepathMod.MOD_ID)
public final class NeoforgeLifepath {

	public NeoforgeLifepath(IEventBus modBus) {
		var platform = new NeoforgePlatform(modBus);
		Platform.init(platform);
		LifepathMod.init();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ClientPlatform.init(new NeoforgeClientPlatform(modBus, platform));
			io.github.durdeuvlad.lifepath.client.LifepathClient.init();
		}
	}
}
