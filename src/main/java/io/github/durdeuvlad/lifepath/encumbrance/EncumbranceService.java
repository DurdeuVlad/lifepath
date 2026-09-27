package io.github.durdeuvlad.lifepath.encumbrance;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import io.github.durdeuvlad.lifepath.resource.ResourceService;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Encumbrance (M9-3, GAMEDESIGN §17): inventory weight → a {@code lifepath:load}
 * resource whose bands (data) apply the movement/stamina penalties. Mechanics
 * are generic — the load resource is an ordinary resource def, so band
 * transitions, sustained effects, and HUD surfacing come free.
 *
 * <p><b>Weights</b> are data: {@code data/<ns>/item_weight/<file>.json} files
 * each contribute a {@code values} map of {@code item_id} or {@code #tag_id}
 * → weight-per-stack-item. Exact item entries win over tag entries; unmapped
 * items use {@code default_item_weight} (config).
 *
 * <p><b>Capacity</b> = {@code capacity} (config) × species
 * {@code capacity_multiplier} × specialization {@code capacity_multiplier}.
 * The computed percent drives the shared 0-100 resource, so band boundaries
 * stay identical across capacities while stronger backs hit them later.
 *
 * <p><b>Cadence:</b> one scan per {@code scan_interval_ticks} (default 40 =
 * 2s) — the cheap-calculation rule; inventory scans are O(slots×tables).
 */
public final class EncumbranceService {
	private EncumbranceService() {
	}

	public static final ResourceLocation CONFIG = LifepathMod.id("encumbrance");
	/** The resource every player carries — written by the scan, never regen'd. */
	public static final ResourceLocation LOAD = LifepathMod.id("load");

	private static long ticks;
	private static boolean initialized;

	/** Registers the low-frequency inventory scan. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK
				.register(server -> {
					if (!LifepathConfig.getBoolean(CONFIG, "enabled")) {
						return;
					}
					int interval = LifepathConfig.getInt(CONFIG, "scan_interval_ticks");
					if (interval <= 0 || ++ticks % interval != 0) {
						return;
					}
					long now = System.currentTimeMillis();
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						try {
							io.github.durdeuvlad.lifepath.perf.PerfCounters.time(
									"encumbrance.scan", () -> scan(
											CharacterManager.getCharacter(player),
											player, now));
						} catch (Exception e) {
							LifepathMod.LOGGER.error("encumbrance scan failed for {}",
									player.getUUID(), e);
						}
					}
				});
	}

	/**
	 * One scan for a player: sum stack weights → capacity-adjusted percent →
	 * write {@link #LOAD} via {@link ResourceService#setTo} so band
	 * transitions/effects fire through the normal resource machinery.
	 * Data-path callable ({@code player} null ⇒ inventory read is skipped —
	 * the service only changes persisted state when it has a live entity).
	 */
	public static void scan(PlayerCharacterData data, @Nullable ServerPlayer player,
			long nowMs) {
		if (LifepathContent.resources().get(LOAD) == null) {
			return; // no load resource def loaded — feature self-disables
		}
		if (player == null) {
			return; // data-path callers cannot supply an inventory
		}
		double pct = loadPercent(data, player);
		double cur = ResourceService.current(data, LOAD);
		// Write only on real change (0.5% hysteresis) — otherwise every scan
		// would dirty the character and re-sync for float noise.
		if (Math.abs(pct - cur) >= 0.5 || data.resources().get(LOAD) == null) {
			ResourceService.setTo(data, player, LOAD, pct, nowMs);
		}
	}

	/** Total carried weight across the whole player inventory (main+armor+offhand). */
	public static double totalWeight(ServerPlayer player) {
		double total = 0.0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			total += weightOf(stack) * stack.getCount();
		}
		return total;
	}

	/** Per-item weight: exact entry → first matching tag entry → default. */
	public static double weightOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		double tagHit = Double.NaN;
		for (Map<String, Double> table : LifepathContent.itemWeights().all().values()) {
			Double exact = table.get(itemId.toString());
			if (exact != null) {
				return exact;
			}
			if (Double.isNaN(tagHit)) {
				for (Map.Entry<String, Double> e : table.entrySet()) {
					String key = e.getKey();
					if (!key.startsWith("#")) {
						continue;
					}
					ResourceLocation tagId = ResourceLocation.tryParse(key.substring(1));
					if (tagId != null && stack.is(
							TagKey.create(BuiltInRegistries.ITEM.key(), tagId))) {
						tagHit = e.getValue();
						break;
					}
				}
			}
		}
		return Double.isNaN(tagHit)
				? LifepathConfig.getDouble(CONFIG, "default_item_weight")
				: tagHit;
	}

	/** Carrying capacity for this character (config × species × spec multipliers). */
	public static double capacityOf(PlayerCharacterData data) {
		double capacity = LifepathConfig.getDouble(CONFIG, "capacity");
		var species = data.speciesId() == null ? null
				: LifepathContent.species().get(data.speciesId());
		if (species != null) {
			capacity *= species.capacityMultiplier();
		}
		var spec = data.specializationId() == null ? null
				: LifepathContent.specializations().get(data.specializationId());
		if (spec != null) {
			capacity *= spec.capacityMultiplier();
		}
		return capacity;
	}

	/** Weight as a percent of effective capacity, clamped to [0, 100]. */
	public static double loadPercent(PlayerCharacterData data, ServerPlayer player) {
		return toPercent(totalWeight(player), capacityOf(data));
	}

	/** Pure percent conversion — zero/negative capacity reads as overloaded. */
	static double toPercent(double weight, double capacity) {
		if (capacity <= 0) {
			return 100.0; // zero-capacity ⇒ always overloaded (data bug, fails heavy)
		}
		return Math.max(0.0, Math.min(100.0, weight / capacity * 100.0));
	}

	/** Test hook. */
	public static void resetForTests() {
		initialized = false;
		ticks = 0;
	}
}
