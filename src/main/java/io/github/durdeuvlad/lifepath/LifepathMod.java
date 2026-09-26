package io.github.durdeuvlad.lifepath;

import io.github.durdeuvlad.lifepath.config.ConfigSpec;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.registry.RegistryBootstrap;
import io.github.durdeuvlad.lifepath.reload.ReloadManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LifepathMod implements ModInitializer {
	public static final String MOD_ID = "lifepath";

	/**
	 * Schema version of persisted Lifepath character data. Bump whenever the
	 * serialized shape changes; M1 migration code reads this to upgrade old saves.
	 */
	public static final int DATA_VERSION = 1;

	/**
	 * Single engine logger (name {@code Lifepath}). Conventions: INFO for
	 * lifecycle events, WARN for recoverable problems, ERROR for failures.
	 * Never use {@code System.out}.
	 */
	public static final Logger LOGGER = LoggerFactory.getLogger("Lifepath");

	@Override
	public void onInitialize() {
		LOGGER.info("Lifepath initializing (version {}, data version {})", modVersion(), DATA_VERSION);

		LifepathConfig.define(LifepathConfig.GENERAL, ConfigSpec.builder()
				.define("debug_logging", false,
						"Enable verbose dev logging. The LIFEPATH_DEBUG env var also forces this on.")
				.build());

		ReloadManager.register(id("engine_config"), LifepathConfig::reload);
		ReloadManager.init();
		LifepathConfig.loadAll();

		RegistryBootstrap.bootstrap();
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	public static String modVersion() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("dev");
	}
}
