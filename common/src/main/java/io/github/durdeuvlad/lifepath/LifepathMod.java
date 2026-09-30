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
import io.github.durdeuvlad.lifepath.skill.DiminishingReturns;
import io.github.durdeuvlad.lifepath.skill.SkillDecayService;
import io.github.durdeuvlad.lifepath.skill.SkillXpService;
import io.github.durdeuvlad.lifepath.skill.XpSourceRouter;

import io.github.durdeuvlad.lifepath.platform.Platform;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LifepathMod {
	public static final String MOD_ID = "lifepath";

	private LifepathMod() {
	}

	/**
	 * Schema version of persisted Lifepath character data. Bump whenever the
	 * serialized shape changes; M1 migration code reads this to upgrade old saves.
	 */
	/** v2: conditions become a condition-id → stage-state map (M9-1).
	 *  v3: optional {@code morph} state record added (morph feature M-1). */
	public static final int DATA_VERSION = 3;

	/**
	 * Single engine logger (name {@code Lifepath}). Conventions: INFO for
	 * lifecycle events, WARN for recoverable problems, ERROR for failures.
	 * Never use {@code System.out}.
	 */
	public static final Logger LOGGER = LoggerFactory.getLogger("Lifepath");

	/// Bootstrap. Called by the loader entrypoint after Platform.init.
	public static void init() {
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
				.define("engineering_xp_multiplier", 1.0, v -> v >= 0 && v <= 1000,
						"Per-skill XP multiplier for lifepath:engineering.")
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
		LifepathConfig.define(SkillDecayService.DECAY_CONFIG, ConfigSpec.builder()
				.define("enabled", true,
						"Master switch for skill decay (GAMEDESIGN §9).")
				.define("grace_hours", 48.0, v -> v >= 0 && v <= 87600,
						"Real hours after last meaningful use before decay starts.")
				.define("maintenance_minutes", 60.0, v -> v >= 0 && v <= 14400,
						"Minutes between the low-frequency decay maintenance pass"
								+ " over online players. 0 disables the pass (login/award/"
								+ "inspect triggers still apply).")
				.define("band_1_upper", 25, v -> v >= 0 && v <= 100,
						"Upper bound of decay band 1 (default 0–25: no decay).")
				.define("band_1_rate", 0.0, v -> v >= 0 && v <= 100,
						"Levels lost per real day inside band 1 (should be 0).")
				.define("band_2_upper", 50, v -> v >= 0 && v <= 100,
						"Upper bound of decay band 2.")
				.define("band_2_rate", 0.05, v -> v >= 0 && v <= 100,
						"Levels lost per real day inside band 2 (26–50).")
				.define("band_3_upper", 75, v -> v >= 0 && v <= 100,
						"Upper bound of decay band 3.")
				.define("band_3_rate", 0.10, v -> v >= 0 && v <= 100,
						"Levels lost per real day inside band 3 (51–75).")
				.define("band_4_upper", 90, v -> v >= 0 && v <= 100,
						"Upper bound of decay band 4.")
				.define("band_4_rate", 0.20, v -> v >= 0 && v <= 100,
						"Levels lost per real day inside band 4 (76–90).")
				.define("band_5_upper", 100, v -> v >= 0 && v <= 100,
						"Upper bound of decay band 5 (top).")
				.define("band_5_rate", 0.35, v -> v >= 0 && v <= 100,
						"Levels lost per real day inside band 5 (91–100).")
				.build());
		LifepathConfig.define(id("abilities"), ConfigSpec.builder()
				.define("enabled", true,
						"Master switch for the ability system: passive sweeps,"
								+ " event dispatch, activation requests, and"
								+ " damage_taken modifiers all no-op when off.")
				.define("passive_interval_ticks", 20, v -> v >= 1 && v <= 1200,
						"Engine tick interval for PASSIVE abilities (each ability's"
								+ " own interval_ticks is additionally honored).")
				.define("nearby_max_radius", 32, v -> v >= 1 && v <= 64,
						"Radius cap for block_nearby/entity_nearby conditions"
								+ " (params clamp to this).")
				.define("cooldown_multiplier", 1.0, v -> v >= 0 && v <= 100,
						"Global multiplier on every ability's declared cooldown"
								+ " (M4-4). Applies to newly triggered cooldowns;"
								+ " 0 makes future cooldowns a no-op.")
				.define("persist_min_seconds", 5.0, v -> v >= 0 && v <= 3600,
						"Ability cooldowns with <= this many seconds remaining"
								+ " at load do not survive relog (M4-4).")
				.build());
		LifepathConfig.define(id("resources"), ConfigSpec.builder()
				.define("tick_interval_ticks", 20, v -> v >= 1 && v <= 1200,
						"Resource regen/band sweep interval (M4-5) — regen is"
								+ " tick-batched at this resolution, not per-tick.")
				.build());
		LifepathConfig.define(DiminishingReturns.CONFIG, ConfigSpec.builder()
				.define("enabled", true,
						"Diminishing returns on repeated identical actions (GAMEDESIGN §11.1).")
				.define("window_hours", 4.0, v -> v >= 0.0167 && v <= 720,
						"Rolling window (real hours) over which identical action"
								+ " signatures are counted. Older entries age out.")
				.define("tier_1_count", 64, v -> v >= 1 && v <= 4096,
						"In-window count up to this gets tier_1_multiplier."
								+ " Capped at the ledger's 4096-entry cap.")
				.define("tier_1_multiplier", 1.0, v -> v >= 0 && v <= 10,
						"XP multiplier for counts <= tier_1_count.")
				.define("tier_2_count", 256, v -> v >= 1 && v <= 4096,
						"In-window count up to this gets tier_2_multiplier."
								+ " Capped at the ledger's 4096-entry cap.")
				.define("tier_2_multiplier", 0.5, v -> v >= 0 && v <= 10,
						"XP multiplier for counts between the two tiers.")
				.define("tier_3_multiplier", 0.1, v -> v >= 0 && v <= 10,
						"XP multiplier for counts above tier_2_count.")
				.define("signature_cap", 4096, v -> v >= 64 && v <= 4096,
						"Anti-exploit bound: max timestamps kept per action"
								+ " signature. Capped at the persistence bound (4096).")
				.build());
		LifepathConfig.define(io.github.durdeuvlad.lifepath.encumbrance.EncumbranceService.CONFIG,
				ConfigSpec.builder()
				.define("enabled", true,
						"Encumbrance: inventory weight drives the lifepath:load resource bands (M9-3).")
				.define("capacity", 400.0, v -> v > 0 && v <= 1.0e6,
						"Base carry capacity before species/spec multipliers. "
								+ "Load% = total weight / capacity * 100.")
				.define("default_item_weight", 1.0, v -> v >= 0 && v <= 1.0e4,
						"Weight per item for stacks with no item_weight entry.")
				.define("container_contents_factor", 0.75, v -> v >= 0 && v <= 1.0,
						"Fraction of contents weight a container still imposes "
								+ "(sacks/bundles/shulkers). Per-item override: "
								+ "'<id>@contents' entries in item_weight tables.")
				.define("scan_interval_ticks", 40, v -> v >= 10 && v <= 1200,
						"Inventory scan cadence — cheap-calculation rule: "
								+ "bands react within this window, never per-tick.")
				.build());
		LifepathConfig.define(LifepathConfig.CLIENT, ConfigSpec.builder()
				.define("hud_enabled", true,
						"Show the Lifepath HUD overlay (resources, cooldowns).")
				.define("hud_position", "top_left",
						v -> java.util.List.of("top_left", "top_right",
								"bottom_left", "bottom_right").contains(v),
						"HUD anchor corner: top_left|top_right|bottom_left|bottom_right.")
				.define("hud_scale", 1.0, v -> v >= 0.5 && v <= 2.0,
						"HUD render scale multiplier (readable at GUI scales 1-4).")
				.define("onboarding_auto_open", true,
						"Pop the species picker once per session on join while you"
								+ " haven't picked a species. The Character screen's"
								+ " Choose button works regardless.")
				.build());

		ReloadManager.register(id("engine_config"), LifepathConfig::reload);
		ReloadManager.init();
		LifepathConfig.loadAll();

		LifepathCommands.init();
		CharacterCommands.init();
		io.github.durdeuvlad.lifepath.command.SpecializationCommands.init();
		io.github.durdeuvlad.lifepath.command.SpeciesCommands.init();
		io.github.durdeuvlad.lifepath.command.DebugCommands.init();
		io.github.durdeuvlad.lifepath.command.ConditionCommands.init();
		io.github.durdeuvlad.lifepath.command.AttunementCommands.init();
		io.github.durdeuvlad.lifepath.command.UnlockCommands.init();
		io.github.durdeuvlad.lifepath.encumbrance.EncumbranceService.init();

		RegistryBootstrap.register(id("character_attachments"), CharacterAttachments::init);
		RegistryBootstrap.bootstrap();

		LifepathContent.init();
		LifepathNetworking.registerS2C(CharacterSyncPayload.ID, CharacterSyncPayload.PACKET_CODEC);
		LifepathNetworking.registerS2C(
				io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload.ID,
				io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload.PACKET_CODEC);
		LifepathNetworking.registerS2C(
				io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload.ID,
				io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload.PACKET_CODEC);
		LifepathNetworking.registerS2C(
				io.github.durdeuvlad.lifepath.network.s2c.HighlightEntitiesPayload.ID,
				io.github.durdeuvlad.lifepath.network.s2c.HighlightEntitiesPayload.PACKET_CODEC);
		LifepathNetworking.registerS2C(
				io.github.durdeuvlad.lifepath.network.s2c.CooldownUpdatePayload.ID,
				io.github.durdeuvlad.lifepath.network.s2c.CooldownUpdatePayload.PACKET_CODEC);
		LifepathNetworking.registerS2C(
				io.github.durdeuvlad.lifepath.network.s2c.ResourceUpdatePayload.ID,
				io.github.durdeuvlad.lifepath.network.s2c.ResourceUpdatePayload.PACKET_CODEC);
		CharacterManager.init();
		SkillXpService.init();
		SkillDecayService.init();
		io.github.durdeuvlad.lifepath.skill.SkillMilestoneService.init();
		// Payload type must register before AbilityEngine.init() installs its
		// receiver (onC2S rejects unregistered types).
		LifepathNetworking.registerC2S(
				io.github.durdeuvlad.lifepath.network.c2s.ActivateAbilityPayload.ID,
				io.github.durdeuvlad.lifepath.network.c2s.ActivateAbilityPayload.PACKET_CODEC);
		io.github.durdeuvlad.lifepath.ability.AbilityEngine.init();
		// M-3 (morph): join/respawn re-apply of the active form's stat profile —
		// registered after CharacterManager so the character cache is warm.
		io.github.durdeuvlad.lifepath.morph.MorphService.init();
		io.github.durdeuvlad.lifepath.resource.ResourceService.init();
		io.github.durdeuvlad.lifepath.feedback.FeedbackService.init();
		// M14: GUI-first identity onboarding — per-player selection catalog +
		// server-validated select requests. Types register before the service
		// installs receivers (onC2S rejects unregistered types), and the
		// service inits after CharacterManager so its join listener runs with
		// the character cache already populated.
		LifepathNetworking.registerS2C(
				io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.ID,
				io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.PACKET_CODEC);
		LifepathNetworking.registerC2S(
				io.github.durdeuvlad.lifepath.network.c2s.RequestSelectionCatalogPayload.ID,
				io.github.durdeuvlad.lifepath.network.c2s.RequestSelectionCatalogPayload.PACKET_CODEC);
		LifepathNetworking.registerC2S(
				io.github.durdeuvlad.lifepath.network.c2s.SelectSpeciesPayload.ID,
				io.github.durdeuvlad.lifepath.network.c2s.SelectSpeciesPayload.PACKET_CODEC);
		LifepathNetworking.registerC2S(
				io.github.durdeuvlad.lifepath.network.c2s.SelectSpecializationPayload.ID,
				io.github.durdeuvlad.lifepath.network.c2s.SelectSpecializationPayload.PACKET_CODEC);
		io.github.durdeuvlad.lifepath.selection.SelectionService.init();
		LifepathNetworking.registerS2C(
				io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload.ID,
				io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload.PACKET_CODEC);
		XpSourceRouter.init();
		VanillaGameplayProducers.init();
		// M7-1: adapters arrive via the lifepath:adapter entrypoint seam —
		// core never names an external mod (grep-clean compat boundary).
		io.github.durdeuvlad.lifepath.compat.ExternalAdapterRegistry.init();
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	public static String modVersion() {
		return Platform.get().modVersion(MOD_ID).orElse("dev");
	}
}
