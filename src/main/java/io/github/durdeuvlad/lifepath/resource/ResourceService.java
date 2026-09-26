package io.github.durdeuvlad.lifepath.resource;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.EvalContext;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.TargetContext;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition.SpecNode;
import io.github.durdeuvlad.lifepath.content.ResourceDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.network.s2c.ResourceUpdatePayload;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The generic player-resource framework (M4-5, GAMEDESIGN §14/§18): bounded,
 * data-defined meters (temperature, mana, stamina — all the same mechanism)
 * backed by {@link PlayerCharacterData#resources()} and driven by
 * {@link ResourceDefinition} files.
 *
 * <p><b>Single mutation choke point:</b> every resource write routes through
 * {@link #modify}/{@link #setTo} → {@link #apply}, which clamps to the
 * definition's bounds, detects band crossings, fires band effects, and pushes
 * a {@link ResourceUpdatePayload} delta. {@code modify_resource} actions,
 * ability costs, {@code resource_interactions}, and the regen sweep all share
 * this path — no caller writes {@code setResource} directly.
 *
 * <p><b>Update strategy (cheap-calculation rule):</b> tick-batched — one
 * sweep per {@code resources.toml tick_interval_ticks} (default 20 = ~1s)
 * applies {@code regenPerSecond × interval} and refreshes sustained band
 * effects. Reads are lazy: an unmaterialized resource resolves to its
 * definition's {@code default} without writing state.
 *
 * <p><b>Ownership:</b> the sweep visits resources the player carries
 * (materialized) or their species declares ({@code SpeciesDefinition.resources}).
 * Band transitions emit {@code lifepath:resource_band_enter|exit} activity
 * events so EVENT abilities can react to meter crossings in pure data.
 */
public final class ResourceService {
	private ResourceService() {}

	/** Activity type emitted when a resource enters a band (attribute {@code band}=index). */
	public static final Identifier BAND_ENTER = LifepathMod.id("resource_band_enter");
	/** Activity type emitted when a resource leaves a band. */
	public static final Identifier BAND_EXIT = LifepathMod.id("resource_band_exit");

	/**
	 * Reentrancy guard: a band-entry action that itself mutates resources can
	 * cascade transitions; depth-capped so pathological data can't loop
	 * forever (actions beyond the cap are skipped with an ERROR).
	 */
	private static final int MAX_TRANSITION_DEPTH = 4;
	private static int transitionDepth;
	private static long ticks;
	private static boolean initialized;

	/** Registers the tick-batched regen/band sweep. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		// After CharacterManager's JOIN snapshot (registered earlier in init):
		// band indices ride deltas only, so quiescent resources need a push.
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN
				.register((handler, sender, server) -> sendSnapshotDeltas(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int interval = tickIntervalTicks();
			if (++ticks % interval != 0) {
				return;
			}
			long now = System.currentTimeMillis();
			double dtSeconds = interval / 20.0;
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				try {
					tickPlayer(CharacterManager.getCharacter(player), player, now, dtSeconds);
				} catch (Exception e) {
					LifepathMod.LOGGER.error("resource sweep failed for {}", player.getUuid(), e);
				}
			}
		});
	}

	/**
	 * The resource's current value — a materialized state wins; otherwise the
	 * definition's {@code default}; unknown resources read as 0 (fail closed).
	 * Pure read — never materializes.
	 */
	public static double current(PlayerCharacterData data, Identifier resourceId) {
		var state = data.resources().get(resourceId);
		if (state != null) {
			// Clamp on read: a def reload may have shrunk bounds under a stored
			// value — conditions/costs must never see an out-of-range number.
			ResourceDefinition def = LifepathContent.resources().get(resourceId);
			double lo = def != null ? def.min() : state.min();
			double hi = def != null ? def.max() : state.max();
			double cur = state.current();
			return !Double.isFinite(cur) ? (def != null ? def.defaultValue() : state.min())
					: Math.max(lo, Math.min(hi, cur));
		}
		ResourceDefinition def = LifepathContent.resources().get(resourceId);
		return def != null ? def.defaultValue() : 0.0;
	}

	/** Applies {@code delta} through the definition's bounds; returns the new value. */
	public static double modify(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			Identifier resourceId, double delta, long nowMs) {
		return apply(data, player, resourceId, current(data, resourceId) + delta, nowMs);
	}

	/** Sets the resource to {@code value} through the definition's bounds. */
	public static double setTo(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			Identifier resourceId, double value, long nowMs) {
		return apply(data, player, resourceId, value, nowMs);
	}

	/**
	 * One batched tick for a player: regen (+ band transitions that regen
	 * causes) and sustained-effect refresh for every owned resource.
	 * Data-path callable for tests ({@code player} may be null — status
	 * effects simply don't apply; transitions and events still run).
	 */
	static void tickPlayer(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			long nowMs, double intervalSeconds) {
		Set<Identifier> owned = new LinkedHashSet<>(data.resources().keySet());
		SpeciesDefinition species = data.speciesId() == null ? null
				: LifepathContent.species().get(data.speciesId());
		if (species != null) {
			owned.addAll(species.resources());
		}
		boolean changed = false;
		for (Identifier id : owned) {
			ResourceDefinition def = LifepathContent.resources().get(id);
			if (def == null) {
				continue;
			}
			double cur = current(data, id);
			// Sustained band effects refresh every sweep while inside.
			int band = ResourceDefinition.bandOf(def, cur);
			if (band >= 0) {
				applyBandEffects(player, def.bands().get(band));
			}
			if (def.regenPerSecond() != 0.0) {
				double next = apply(data, player, id,
						cur + def.regenPerSecond() * intervalSeconds, nowMs);
				changed |= next != cur;
			}
		}
		if (changed && player != null) {
			CharacterManager.markDirty(player);
		}
	}

	// ------------------------------------------------------------------

	private static double apply(PlayerCharacterData data, @Nullable ServerPlayerEntity player,
			Identifier resourceId, double requested, long nowMs) {
		ResourceDefinition def = LifepathContent.resources().get(resourceId);
		var stored = data.resources().get(resourceId);
		if (def == null && stored == null) {
			return 0.0; // unknown, unmaterialized — nothing to mutate
		}
		double lo = def != null ? def.min() : stored.min();
		double hi = def != null ? def.max() : stored.max();
		double old = current(data, resourceId);
		double next = Math.max(lo, Math.min(hi, requested));
		if (!Double.isFinite(next)) {
			return old; // never persist NaN/Inf
		}
		data.setResource(resourceId,
				new PlayerCharacterData.ResourceState(next, lo, hi));
		if (next != old) {
			int toBand = ResourceDefinition.bandOf(def, next);
			// Delta + events emit BEFORE entry actions run — a band action may
			// mutate THIS same resource; its nested packet/event must land
			// last so client + bus state resolve to the resting value.
			if (player != null) {
				try {
					ServerPlayNetworking.send(player, new ResourceUpdatePayload(
							resourceId, next, lo, hi, toBand));
				} catch (Exception e) {
					LifepathMod.LOGGER.error("resource {} sync failed", resourceId, e);
				}
				CharacterManager.markDirty(player);
			}
			onBandTransition(data, player, resourceId, def,
					ResourceDefinition.bandOf(def, old), toBand, nowMs);
			// Entry actions may have re-mutated — report the resting value.
			var resting = data.resources().get(resourceId);
			return resting != null ? resting.current() : next;
		}
		return next;
	}

	private static void onBandTransition(PlayerCharacterData data,
			@Nullable ServerPlayerEntity player, Identifier resourceId,
			@Nullable ResourceDefinition def, int fromBand, int toBand, long nowMs) {
		if (fromBand == toBand) {
			return;
		}
		if (transitionDepth >= MAX_TRANSITION_DEPTH) {
			LifepathMod.LOGGER.error("resource {} band-transition depth cap hit — "
					+ "check for action-driven oscillation in its bands", resourceId);
			return;
		}
		transitionDepth++;
		try {
			if (fromBand >= 0) {
				stripBandEffects(player, def.bands().get(fromBand));
				publish(player, BAND_EXIT, resourceId, fromBand, nowMs);
			}
			if (toBand >= 0) {
				ResourceDefinition.Band band = def.bands().get(toBand);
				applyBandEffects(player, band);
				// Event BEFORE entry actions — nested mutations publish after
				// this, so observers' last event reflects the resting band.
				publish(player, BAND_ENTER, resourceId, toBand, nowMs);
				runEntryActions(data, player, resourceId, band, nowMs);
			}
		} finally {
			transitionDepth--;
		}
	}

	/** Sustained channel: (re)applies a band's status effects — call on entry and every sweep inside. */
	private static void applyBandEffects(@Nullable ServerPlayerEntity player,
			ResourceDefinition.Band band) {
		if (player == null) {
			return;
		}
		for (ResourceDefinition.BandEffect fx : band.effects()) {
			var entry = Registries.STATUS_EFFECT.getEntry(fx.effect()).orElse(null);
			if (entry != null) {
				player.addStatusEffect(new StatusEffectInstance(entry,
						fx.durationTicks(), fx.amplifier()));
			}
		}
	}

	/**
	 * Removes a band's named effects on exit. Limitation: status effects carry
	 * no source marker — this strips the effect type even if another source
	 * also applied it (documented; pick band-exclusive effects in data).
	 */
	private static void stripBandEffects(@Nullable ServerPlayerEntity player,
			ResourceDefinition.Band band) {
		if (player == null) {
			return;
		}
		for (ResourceDefinition.BandEffect fx : band.effects()) {
			Registries.STATUS_EFFECT.getEntry(fx.effect())
					.ifPresent(player::removeStatusEffect);
		}
	}

	/** Entry channel: runs a band's embedded actions exactly once, exception-isolated. */
	private static void runEntryActions(PlayerCharacterData data,
			@Nullable ServerPlayerEntity player, Identifier resourceId,
			ResourceDefinition.Band band, long nowMs) {
		if (band.actions().isEmpty()) {
			return;
		}
		// The resource id namespaces the EvalContext — modify_attribute
		// modifiers applied by band actions get a stable, unique id domain.
		EvalContext ctx = new EvalContext(player, data, nowMs, resourceId);
		TargetContext target = new TargetContext(player, data);
		for (SpecNode node : band.actions()) {
			var exec = AbilityVocabulary.action(node.type());
			if (exec == null) {
				LifepathMod.LOGGER.debug("resource {} band action {} not registered",
						resourceId, node.type());
				continue;
			}
			try {
				exec.run(target, ctx, node.raw());
			} catch (Exception e) {
				LifepathMod.LOGGER.error("resource {} band action {} threw",
						resourceId, node.type(), e);
			}
		}
	}

	private static void publish(@Nullable ServerPlayerEntity player, Identifier type,
			Identifier resourceId, int bandIndex, long nowMs) {
		ActivityDispatcher.publish(new ActivityEvent(player, type, resourceId,
				Set.of(), ActivityEvent.Cause.SYSTEM, nowMs,
				Map.of("band", Integer.toString(bandIndex))));
	}

	/**
	 * Join-time delta sync (M4-5/m6): the character snapshot carries values but
	 * not band indices — emit one delta per owned resource right after the
	 * initial snapshot so a quiescent meter still reports its band for M6.
	 * Registered after {@code CharacterManager}'s JOIN sync so deltas land last.
	 */
	private static void sendSnapshotDeltas(ServerPlayerEntity player) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		for (Identifier id : data.resources().keySet()) {
			ResourceDefinition def = LifepathContent.resources().get(id);
			var s = data.resources().get(id);
			ServerPlayNetworking.send(player, new ResourceUpdatePayload(id,
					s.current(), s.min(), s.max(),
					ResourceDefinition.bandOf(def, s.current())));
		}
	}

	private static int tickIntervalTicks() {
		return ((Number) LifepathConfig.getOrDefault(
				LifepathMod.id("resources"), "tick_interval_ticks", 20)).intValue();
	}
}
