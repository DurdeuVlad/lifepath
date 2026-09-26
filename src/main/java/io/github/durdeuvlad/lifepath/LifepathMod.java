package io.github.durdeuvlad.lifepath;

import io.github.durdeuvlad.lifepath.character.CharacterAttachments;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.command.CharacterCommands;
import io.github.durdeuvlad.lifepath.command.LifepathCommands;
import io.github.durdeuvlad.lifepath.config.ConfigSpec;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import io.github.durdeuvlad.lifepath.network.s2c.CharacterSyncPayload;
import io.github.durdeuvlad.lifepath.producer.VanillaGameplayProducers;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.registry.RegistryBootstrap;
import io.github.durdeuvlad.lifepath.reload.ReloadManager;
import io.github.durdeuvlad.lifepath.skill.SkillXpService;
import io.github.durdeuvlad.lifepath.skill.XpSourceRouter;
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
		LifepathConfig.define(id("character"), ConfigSpec.builder()
				.define("flush_interval_ticks", CharacterManager.DEFAULT_FLUSH_INTERVAL_TICKS,
						value -> value >= 200,
						"Ticks between periodic flushes of dirty character data to the persistent"
								+ " attachment. Data is also saved on disconnect and server stop;"
								+ " this is only a crash-loss window control. Minimum 200.")
				.build());
		LifepathConfig.define(id("skills"), ConfigSpec.builder()
				.define("band_untrained", 0, value -> value == 0,
						"First level of the Untrained band — must stay 0.")
				.define("band_novice", 1, value -> value > 0 && value <= 100,
						"First level of the Novice band.")
				.define("band_apprentice", 20, value -> value > 0 && value <= 100,
						"First level of the Apprentice band.")
				.define("band_skilled", 40, value -> value > 0 && value <= 100,
						"First level of the Skilled band.")
				.define("band_expert", 60, value -> value > 0 && value <= 100,
						"First level of the Expert band.")
				.define("band_master", 80, value -> value > 0 && value <= 100,
						"First level of the Master band.")
				.define("band_legendary", 95, value -> value > 0 && value <= 100,
						"First level of the Legendary band.")
				.define("global_xp_multiplier", 1.0,
						value -> value instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() <= 1000,
						"Global XP multiplier applied by the lifepath:global_multiplier modifier."
								+ " 0 disables all XP gain; sane values are 0.1–10.")
				.define("mining_xp_multiplier", 1.0, v -> v >= 0 && v <= 1000,
						"Per-skill XP multiplier for lifepath:mining.")
				.define("farming_xp_multiplier", 1.0, v -> v >= 0 && v <= 1000,
						"Per-skill XP multiplier for lifepath:farming.")
				.define("smithing_xp_multiplier", 1.0, v -> v >= 0 && v <= 1000,
						"Per-skill XP multiplier for lifepath:smithing.")
				.define("fishing_xp_multiplier", 1.0, v -> v >= 0 && v <= 1000,
						"Per-skill XP multiplier for lifepath:fishing.")
				.define("foraging_xp_multiplier", 1.0, v -> v >= 0 && v <= 1000,
						"Per-skill XP multiplier for lifepath:foraging.")
				.define("aptitude_d_xp_multiplier", 0.70, v -> v >= 0 && v <= 100,
						"XP multiplier for aptitude grade D (GAMEDESIGN §8).")
				.define("aptitude_c_xp_multiplier", 0.90, v -> v >= 0 && v <= 100,
						"XP multiplier for aptitude grade C.")
				.define("aptitude_b_xp_multiplier", 1.00, v -> v >= 0 && v <= 100,
						"XP multiplier for aptitude grade B.")
				.define("aptitude_a_xp_multiplier", 1.25, v -> v >= 0 && v <= 100,
						"XP multiplier for aptitude grade A.")
				.define("aptitude_s_xp_multiplier", 1.50, v -> v >= 0 && v <= 100,
						"XP multiplier for aptitude grade S.")
				.define("aptitude_d_decay_multiplier", 1.25, v -> v >= 0 && v <= 100,
						"Decay-rate multiplier for grade D (consumed by M3-3).")
				.define("aptitude_c_decay_multiplier", 1.10, v -> v >= 0 && v <= 100,
						"Decay-rate multiplier for grade C.")
				.define("aptitude_b_decay_multiplier", 1.00, v -> v >= 0 && v <= 100,
						"Decay-rate multiplier for grade B.")
				.define("aptitude_a_decay_multiplier", 0.80, v -> v >= 0 && v <= 100,
						"Decay-rate multiplier for grade A.")
				.define("aptitude_s_decay_multiplier", 0.60, v -> v >= 0 && v <= 100,
						"Decay-rate multiplier for grade S.")
				.define("unmapped_sources_award_xp", true,
						"When false, activity events matching no per_subject/per_tag entry"
								+ " grant nothing (base_xp ignored). When true, unmapped"
								+ " sources yield the file's base_xp.")
				.build());

		ReloadManager.register(id("engine_config"), LifepathConfig::reload);
		ReloadManager.init();
		LifepathConfig.loadAll();

		LifepathCommands.init();
		CharacterCommands.init();
		io.github.durdeuvlad.lifepath.command.SpecializationCommands.init();

		RegistryBootstrap.register(id("character_attachments"), CharacterAttachments::init);
		RegistryBootstrap.bootstrap();

		LifepathContent.init();
		LifepathNetworking.registerS2C(CharacterSyncPayload.ID, CharacterSyncPayload.PACKET_CODEC);
		CharacterManager.init();
		SkillXpService.init();
		XpSourceRouter.init();
		VanillaGameplayProducers.init();
		io.github.durdeuvlad.lifepath.compat.ExternalAdapterRegistry.init();
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
