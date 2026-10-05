package com.dwurdy.lifepath.ability;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.ability.AbilityVocabulary.EvalContext;
import com.dwurdy.lifepath.ability.AbilityVocabulary.TargetContext;
import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.config.LifepathConfig;
import com.dwurdy.lifepath.content.AbilityDefinition;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.content.SpecializationDefinition;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.network.LifepathNetworking;
import com.dwurdy.lifepath.network.c2s.ActivateAbilityPayload;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The ability engine (M4-1, GAMEDESIGN §12): evaluates composed abilities —
 * trigger → conditions (all-of + any-of) → targets → cost/cooldown → actions.
 *
 * <p><b>Triggers:</b> PASSIVE re-evaluates at {@code abilities.toml}
 * {@code passive_interval_ticks}; EVENT fires off the M2-3 activity bus; ACTIVE
 * arrives via {@code C2S lifepath:ability/activate} and is fully re-validated
 * server-side (ownership, cooldown, conditions) — a forged packet does nothing.
 *
 * <p><b>Ownership</b> is derived, never stored: the union of the character's
 * species passive/active refs, specialization signature refs, and
 * trait/condition/attunement id refs (those content domains land later; ids in
 * the lists that resolve to ability definitions already count).
 *
 * <p>Evaluation failures are DEBUG-logged; malformed files fail at load time.
 */
public final class AbilityEngine {
	private AbilityEngine() {}

	public enum Outcome {
		EXECUTED, NOT_OWNED, ON_COOLDOWN, COST_UNMET, CONDITIONS_FAILED,
		NO_TARGETS, UNKNOWN_ABILITY, WRONG_TRIGGER, DISABLED
	}

	private static long ticks;
	private static boolean initialized;

	/**
	 * Schedule-map key for {@code grant_flight} grants (M17): {@code mayfly}
	 * persists in player NBT, so the grant's expiry must persist too —
	 * the cooldown map's schedule-marker slot survives logout/restart, and
	 * {@link #reconcileFlight} revokes only grants it can prove expired.
	 * The {@code engine/} path keeps the marker out of the flat ability-id
	 * space — a shipped {@code ability/flight_grant.json} could otherwise
	 * alias its own schedule marker with this one.
	 */
	static final ResourceLocation FLIGHT_GRANT_ID = LifepathMod.id("engine/flight_grant");

	/** Revocation cadence while the ability system is disabled (~1s). */
	private static final int FLIGHT_RECONCILE_INTERVAL_TICKS = 20;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AbilityVocabulary.init();
		// PASSIVE: coarse engine tick (config); each ability's own
		// interval_ticks is honored via a schedule marker in the cooldown map
		// (namespaced key — ability state stays in the generic maps per spec).
		com.dwurdy.lifepath.platform.Platform.get().onEndServerTick(server -> {
					BuiltinActions.onServerTick(server);
					++ticks;
					int interval = abilitiesEnabled() ? passiveIntervalTicks() : 0;
					if (interval <= 0) {
						// Grant revocation is safety bookkeeping, not ability
						// eval — a lifepath-granted mayfly must still lapse
						// while the system is off (or the interval is
						// misconfigured), else it would persist forever.
						if (ticks % FLIGHT_RECONCILE_INTERVAL_TICKS == 0) {
							long nowMs = System.currentTimeMillis();
							for (ServerPlayer player : server.getPlayerList()
									.getPlayers()) {
								reconcileFlight(CharacterManager
										.getCharacter(player), player, nowMs);
							}
						}
						return;
					}
					if (ticks % interval != 0) {
						return;
					}
					long now = System.currentTimeMillis();
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						com.dwurdy.lifepath.perf.PerfCounters.time(
								"ability.passive_sweep", () -> {
									var data = CharacterManager.getCharacter(player);
									runPassiveSweep(data, player, now);
									// M9-1: stage advance_after_seconds rides
									// the same per-player sweep interval.
									com.dwurdy.lifepath.condition.ConditionService
											.tick(data, player, now);
								});
					}
				});
		// EVENT: one bus listener; per-event filtering keeps the domain data-driven.
		ActivityDispatcher.registerAny(event -> {
			ServerPlayer player = event.player();
			if (player == null) {
				return;
			}
			var data = CharacterManager.getCharacter(player);
			handleEvent(data, player, event.type(), System.currentTimeMillis());
			// M9-1: advance_events count toward condition stage progress.
			com.dwurdy.lifepath.condition.ConditionService
					.onActivity(data, player, event, System.currentTimeMillis());
			// M9-2: event-type attunement acquisition rolls on the same event.
			com.dwurdy.lifepath.attunement.AttunementService
					.onActivity(data, player, event, System.currentTimeMillis());
			// M9-4: event-type unlock sources roll on the same event.
			com.dwurdy.lifepath.unlock.UnlockService
					.onActivity(data, player, event, System.currentTimeMillis());
		});
		// ACTIVE: server re-validates the request end-to-end.
		LifepathNetworking.onC2S(ActivateAbilityPayload.ID, (payload, player) -> {
			var server = player.getServer();
			if (server == null) {
				return;
			}
			server.execute(() -> {
				// The queued task can run after a disconnect — resolving a
				// character for an offline entity would leak a cache entry.
				if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
						// Resolve AUTO up front so denial feedback names the
						// real ability (and its real cooldown), not the sentinel.
						// tryActivate resolves again internally — keeping the
						// DISABLED-first validation order the single authority.
						ResourceLocation abilityId = resolveActivationTarget(
								CharacterManager.getCharacter(player),
								payload.abilityId());
						Outcome outcome = tryActivate(player, payload.abilityId());
						if (outcome != Outcome.EXECUTED) {
							com.dwurdy.lifepath.feedback.FeedbackService
									.abilityDenied(player,
											abilityId != null ? abilityId
													: payload.abilityId(),
											outcome);
						}
					}
				});
		});
	}

	/**
	 * The derived owned-ability id set: species passive+active refs +
	 * specialization signature refs + any trait/condition/attunement/unlock id
	 * that resolves to an ability definition. Missing defs contribute nothing.
	 */
	public static Set<ResourceLocation> ownedAbilities(PlayerCharacterData data) {
		Set<ResourceLocation> owned = new LinkedHashSet<>();
		if (data.speciesId() != null) {
			SpeciesDefinition species = LifepathContent.species().get(data.speciesId());
			if (species != null) {
				owned.addAll(species.passiveAbilities());
				owned.addAll(species.activeAbilities());
			}
		}
		if (data.specializationId() != null) {
			SpecializationDefinition spec =
					LifepathContent.specializations().get(data.specializationId());
			if (spec != null) {
				owned.addAll(spec.signatureRefs());
			}
		}
		for (ResourceLocation id : data.traits()) {
			owned.add(id);
		}
		// M9-1: conditions resolve through the service — base + cumulative
		// stage abilities (condition ids in data are NOT ability ids).
		owned.addAll(com.dwurdy.lifepath.condition.ConditionService
				.activeAbilities(data));
		// M9-2: attunements resolve through their service too — held
		// attunement ids are not ability ids either.
		owned.addAll(com.dwurdy.lifepath.attunement.AttunementService
				.activeAbilities(data));
		for (ResourceLocation id : data.unlocks()) {
			owned.add(id);
		}
		return owned;
	}

	/**
	 * First owned ability whose trigger kind is ACTIVE, in owned-set order —
	 * species actives first, then specialization signatures, then
	 * trait/condition/attunement/unlock ids. Null when the player owns none.
	 */
	@Nullable
	private static ResourceLocation firstOwnedActive(PlayerCharacterData data) {
		for (ResourceLocation id : ownedAbilities(data)) {
			AbilityDefinition def = LifepathContent.abilities().get(id);
			if (def != null && def.trigger().kind() == AbilityDefinition.Kind.ACTIVE) {
				return id;
			}
		}
		return null;
	}

	/**
	 * Resolves the {@link ActivateAbilityPayload#AUTO} sentinel to the player's
	 * first owned ACTIVE ability; any other id passes through unchanged. Null
	 * when AUTO was requested and the player owns no ACTIVE ability.
	 */
	@Nullable
	public static ResourceLocation resolveActivationTarget(PlayerCharacterData data,
			ResourceLocation abilityId) {
		return abilityId.equals(ActivateAbilityPayload.AUTO)
				? firstOwnedActive(data)
				: abilityId;
	}

	/**
	 * PASSIVE sweep for one player: honors each ability's {@code interval_ticks}
	 * via a namespaced schedule marker in the cooldown map (generic state —
	 * no ability-specific fields on the model).
	 */
	static void runPassiveSweep(PlayerCharacterData data,
			@Nullable ServerPlayer player, long now) {
		for (ResourceLocation id : ownedAbilities(data)) {
			AbilityDefinition def = LifepathContent.abilities().get(id);
			if (def == null || def.trigger().kind() != AbilityDefinition.Kind.PASSIVE) {
				continue;
			}
			long intervalMs = Math.max(1, def.trigger().intervalTicks()) * 50L;
			Long dueAt = CooldownService.nextDueAt(data, def.id());
			if (dueAt != null && dueAt > now) {
				continue;
			}
			// Elapsed real time since the previous eval (marker stores next-due,
			// so last-run ≈ dueAt − interval) — resource_interactions scale on
			// true elapsed time, not the nominal interval.
			double elapsedSeconds = dueAt != null
					? Math.max(0.0, (now - (dueAt - intervalMs)) / 1000.0)
					: intervalMs / 1000.0;
			CooldownService.markNextDue(data, def.id(), now + intervalMs);
			if (evaluate(data, player, def, now, elapsedSeconds) == Outcome.EXECUTED
					&& player != null) {
				CharacterManager.changed(player);
			}
		}
		// Reconcile AFTER evals: a still-valid grant refreshes its marker
		// during eval, so reconcile must see post-eval state — reconciling
		// first could consume a marker on its exact expiry tick and revoke
		// mayfly one eval before the same sweep re-grants it (flight flicker).
		reconcileFlight(data, player, now);
	}

	/**
	 * Revokes a {@code grant_flight} grant whose persisted marker expired
	 * (conditions stopped refreshing it — e.g. a phoenix in rebirth). Null
	 * marker = lifepath never granted flight → command- and mod-granted
	 * {@code mayfly} are never touched. Creative/spectator are skipped.
	 * The marker is consumed on expiry, so a lapsed grant vetoes {@code
	 * mayfly} at most once — flight granted afterwards by commands or other
	 * mods survives.
	 */
	private static void reconcileFlight(PlayerCharacterData data,
			@Nullable ServerPlayer player, long now) {
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (!consumeExpiredFlightGrant(data, now)) {
			return;
		}
		// Consuming the marker mutates the persisted cooldown map — without a
		// dirty mark the consumption is lost on stop/crash and the stale marker
		// re-materializes, letting the "veto at most once" guarantee fire twice
		// (stripping mayfly that commands/mods granted after the lapse).
		CharacterManager.markDirty(player);
		var ab = player.getAbilities();
		if (ab.mayfly) {
			ab.mayfly = false;
			ab.flying = false;
			player.onUpdateAbilities();
		}
	}

	/**
	 * True when a persisted {@link #FLIGHT_GRANT_ID} marker exists and has
	 * expired — and CONSUMES it. Schedule markers never age out of the
	 * cooldown map on their own, so leaving a stale marker behind would keep
	 * revoking {@code mayfly} that other sources grant long after the
	 * lifepath grant lapsed.
	 */
	static boolean consumeExpiredFlightGrant(PlayerCharacterData data, long now) {
		Long grantedUntil = CooldownService.nextDueAt(data, FLIGHT_GRANT_ID);
		if (grantedUntil == null || grantedUntil > now) {
			return false;
		}
		data.removeCooldown(scheduleKey(FLIGHT_GRANT_ID));
		return true;
	}

	/**
	 * EVENT path: evaluates every owned EVENT ability whose trigger lists
	 * {@code eventType}. One dispatch keeps the domain data-driven — event
	 * types are plain identifiers, never Java branches.
	 */
	static void handleEvent(PlayerCharacterData data,
			@Nullable ServerPlayer player, ResourceLocation eventType, long now) {
		if (!abilitiesEnabled()) {
			return;
		}
		for (ResourceLocation id : ownedAbilities(data)) {
			AbilityDefinition def = LifepathContent.abilities().get(id);
			if (def != null && def.trigger().kind() == AbilityDefinition.Kind.EVENT
					&& def.trigger().events().contains(eventType)
					&& evaluate(data, player, def, now, 0.0) == Outcome.EXECUTED
					&& player != null) {
				CharacterManager.changed(player);
			}
		}
	}

	/**
	 * DAMAGE_TAKEN hook (M5-2): called from the {@code LivingEntity.damage}
	 * injection for every victim. Only {@code damage_taken}-trigger defs owned
	 * by the victim participate; each whose conditions pass multiplies the
	 * amount (stacking multiplicatively). Conditions read the attacker/source/
	 * base amount via {@code EvalContext.damage()}. No actions run here — a
	 * mid-damage action could recurse; load-time validation warns about them.
	 */
	public static float modifyIncomingDamage(net.minecraft.world.entity.Entity entity,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)
				|| player.level().isClientSide()) {
			return amount;
		}
		return modifyIncomingDamage(CharacterManager.getCharacter(player), player,
				new AbilityVocabulary.DamageInfo(source.getEntity(), source, amount),
				amount);
	}

	/**
	 * True when owned {@code damage_taken} defs reduce the hit to zero. The
	 * {@code hurt} head seam uses it to mirror vanilla's fire-resistance
	 * early exit: a fully negated hit returns false before hurtTime, hurt
	 * sound, knockback, or the damage broadcast run — without it a negated
	 * hit still feels like damage (Beta 7 dragonborn report).
	 */
	public static boolean isFullyNegated(net.minecraft.world.entity.Entity entity,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		return amount > 0.0f && modifyIncomingDamage(entity, source, amount) <= 0.0f;
	}

	/** Data-path core (tests): multiplies {@code amount} per passing owned def. */
	static float modifyIncomingDamage(PlayerCharacterData data,
			@Nullable ServerPlayer player, AbilityVocabulary.DamageInfo info,
			float amount) {
		if (!abilitiesEnabled()) {
			return amount;
		}
		float modified = amount;
		long now = System.currentTimeMillis();
		for (ResourceLocation id : ownedAbilities(data)) {
			AbilityDefinition def = LifepathContent.abilities().get(id);
			if (def == null || !def.enabled()
					|| def.trigger().kind() != AbilityDefinition.Kind.DAMAGE_TAKEN
					|| def.trigger().multiplier() == 1.0) {
				continue;
			}
			EvalContext ctx = new EvalContext(player, data, now, def.id(), info);
			try {
				if (conditionsMet(ctx, def)) {
					modified *= (float) def.trigger().multiplier();
				}
			} catch (Exception e) {
				// A buggy condition must never corrupt the damage pipeline.
				LifepathMod.LOGGER.error("damage_taken ability {} threw", def.id(), e);
			}
		}
		return modified;
	}

	/**
	 * DAMAGE_DEALT hook (M14-3): called from the {@code Player.attack} seam
	 * after a landed hit. Each owned {@code damage_dealt} def runs the full
	 * pipeline — conditions read attacker (the player), source, dealt amount,
	 * and the victim via {@code EvalContext.damage()}; {@code victim} targets
	 * resolve the struck entity; {@code cooldown} gates the proc rate.
	 * Server-side only — the seam guards on {@link ServerPlayer}.
	 */
	public static void onDamageDealt(ServerPlayer player,
			net.minecraft.world.entity.Entity victim,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (!abilitiesEnabled()) {
			return;
		}
		onDamageDealt(CharacterManager.getCharacter(player), player,
				new AbilityVocabulary.DamageInfo(player, source, amount, victim),
				System.currentTimeMillis());
	}

	/** Data-path core (tests + the seam): evaluates owned damage_dealt defs. */
	static void onDamageDealt(PlayerCharacterData data,
			@Nullable ServerPlayer player, AbilityVocabulary.DamageInfo info,
			long now) {
		for (ResourceLocation id : ownedAbilities(data)) {
			AbilityDefinition def = LifepathContent.abilities().get(id);
			if (def == null
					|| def.trigger().kind() != AbilityDefinition.Kind.DAMAGE_DEALT) {
				continue;
			}
			if (evaluate(data, player, def, now, 0.0, info) == Outcome.EXECUTED
					&& player != null) {
				CharacterManager.changed(player);
			}
		}
	}

	/** Server-side activation for a C2S request — full validation, never trusts. */
	public static Outcome tryActivate(ServerPlayer player, ResourceLocation abilityId) {
		Outcome outcome = tryActivate(CharacterManager.getCharacter(player), player,
				abilityId, System.currentTimeMillis());
		if (outcome == Outcome.EXECUTED) {
			CharacterManager.changed(player);
		}
		return outcome;
	}

	/**
	 * Data-path activation core (tests + the packet handler): ownership,
	 * trigger kind, cooldown, conditions, cost — then execute.
	 */
	public static Outcome tryActivate(PlayerCharacterData data,
			@Nullable ServerPlayer self, ResourceLocation abilityId, long now) {
		if (!abilitiesEnabled()) {
			return Outcome.DISABLED;
		}
		abilityId = resolveActivationTarget(data, abilityId);
		if (abilityId == null) {
			return Outcome.NOT_OWNED;
		}
		if (!ownedAbilities(data).contains(abilityId)) {
			return Outcome.NOT_OWNED;
		}
		AbilityDefinition def = LifepathContent.abilities().get(abilityId);
		if (def == null) {
			return Outcome.UNKNOWN_ABILITY;
		}
		if (def.trigger().kind() != AbilityDefinition.Kind.ACTIVE) {
			return Outcome.WRONG_TRIGGER;
		}
		return evaluate(data, self, def, now, 0.0);
	}

	/**
	 * The evaluation pipeline: conditions (all-of then any-of) → cooldown →
	 * cost → resolve targets → run actions per target → spend cost, stamp
	 * cooldown, apply resource interactions. Vocabulary evaluators run behind
	 * exception isolation — a buggy datapack spec node can never take the
	 * server tick down (it logs and fails closed instead).
	 *
	 * @param interactionSeconds elapsed seconds for {@code resource_interactions}
	 *        scaling — supplied by the PASSIVE sweep from its schedule marker;
	 *        other trigger kinds pass 0 (interactions are a passive concept).
	 */
	public static Outcome evaluate(PlayerCharacterData data,
			@Nullable ServerPlayer self, AbilityDefinition def, long now,
			double interactionSeconds) {
		return evaluate(data, self, def, now, interactionSeconds, null);
	}

	/**
	 * Evaluation overload carrying a damage context (M14-3 {@code
	 * damage_dealt} procs) — identical pipeline; {@code damage} reaches
	 * conditions and the {@code victim} resolver.
	 */
	public static Outcome evaluate(PlayerCharacterData data,
			@Nullable ServerPlayer self, AbilityDefinition def, long now,
			double interactionSeconds,
			@Nullable AbilityVocabulary.DamageInfo damage) {
		if (!def.enabled()) {
			return debug(def, Outcome.DISABLED);
		}
		EvalContext ctx = new EvalContext(self, data, now, def.id(), damage);
		if (!conditionsMet(ctx, def)) {
			return debug(def, Outcome.CONDITIONS_FAILED);
		}
		if (CooldownService.isOnCooldown(data, def.id(), now)) {
			return debug(def, Outcome.ON_COOLDOWN);
		}
		if (def.cost().isPresent()) {
			AbilityDefinition.Cost cost = def.cost().get();
			// Unmaterialized resources read as their definition's default.
			if (com.dwurdy.lifepath.resource.ResourceService
					.current(data, cost.resource()) < cost.amount()) {
				return debug(def, Outcome.COST_UNMET);
			}
		}
		var resolver = AbilityVocabulary.target(def.target().type());
		if (resolver == null) {
			return debug(def, Outcome.NO_TARGETS);
		}
		List<TargetContext> targets;
		try {
			targets = resolver.resolve(ctx, def.target().raw());
		} catch (Exception e) {
			LifepathMod.LOGGER.error("ability {} target resolver {} threw", def.id(),
					def.target().type(), e);
			return debug(def, Outcome.NO_TARGETS);
		}
		if (targets == null || targets.isEmpty()) {
			return debug(def, Outcome.NO_TARGETS);
		}
		for (TargetContext t : targets) {
			for (AbilityDefinition.SpecNode action : def.actions()) {
				var exec = AbilityVocabulary.action(action.type());
				if (exec == null) {
					LifepathMod.LOGGER.debug("ability {} unknown action {}", def.id(), action.type());
					continue;
				}
				try {
					exec.run(t, ctx, action.raw());
				} catch (Exception e) {
					LifepathMod.LOGGER.error("ability {} action {} threw", def.id(),
							action.type(), e);
				}
			}
			// AoE actions can mutate a PLAYER TARGET's model (resources/XP) —
			// mark+sync theirs too; the caster's own changed() happens in the
			// caller. Non-player/block targets carry no model.
			if (t.data() != null && t.data() != data
					&& t.entity() instanceof ServerPlayer sp) {
				com.dwurdy.lifepath.character.CharacterManager.changed(sp);
			}
		}
		def.cost().ifPresent(cost ->
				com.dwurdy.lifepath.resource.ResourceService.modify(
						data, self, cost.resource(), -cost.amount(), now));
		def.cooldown().ifPresent(cd -> {
			long expiry = CooldownService.trigger(data, def.id(), cd.seconds(), now);
			if (self != null && expiry >= 0) {
				try {
					LifepathNetworking.sendTo(self,
							new com.dwurdy.lifepath.network.s2c
									.CooldownUpdatePayload(def.id(), expiry));
				} catch (Exception e) {
					// Advisory HUD delta — a send failure must not break the eval.
					LifepathMod.LOGGER.error("ability {} cooldown sync failed", def.id(), e);
				}
			}
		});
		applyResourceInteractions(data, self, def, interactionSeconds, now);
		return Outcome.EXECUTED;
	}

	/** Passive-only by contract: scales {@code per_second} by elapsed real time. */
	private static void applyResourceInteractions(PlayerCharacterData data,
			@Nullable ServerPlayer self, AbilityDefinition def,
			double seconds, long now) {
		if (def.resourceInteractions().isEmpty() || seconds <= 0.0) {
			return;
		}
		for (AbilityDefinition.ResourceInteraction ri : def.resourceInteractions()) {
			com.dwurdy.lifepath.resource.ResourceService.modify(
					data, self, ri.resource(), ri.perSecond() * seconds, now);
		}
	}

	private static boolean conditionsMet(EvalContext ctx, AbilityDefinition def) {
		for (AbilityDefinition.SpecNode cond : def.conditions().all()) {
			var eval = AbilityVocabulary.condition(cond.type());
			if (eval == null || !testSafely(def, cond, eval, ctx)) {
				return false;
			}
		}
		if (!def.conditions().any().isEmpty()) {
			for (AbilityDefinition.SpecNode cond : def.conditions().any()) {
				var eval = AbilityVocabulary.condition(cond.type());
				if (eval != null && testSafely(def, cond, eval, ctx)) {
					return true;
				}
			}
			return false;
		}
		return true;
	}

	/** A throwing condition fails closed — a bad spec node can never crash the tick. */
	private static boolean testSafely(AbilityDefinition def, AbilityDefinition.SpecNode cond,
			AbilityVocabulary.ConditionEvaluator eval, EvalContext ctx) {
		try {
			return eval.test(ctx, cond.raw());
		} catch (Exception e) {
			LifepathMod.LOGGER.error("ability {} condition {} threw", def.id(), cond.type(), e);
			return false;
		}
	}

	private static Outcome debug(AbilityDefinition def, Outcome outcome) {
		if ((Boolean) LifepathConfig.getOrDefault(
				LifepathConfig.GENERAL, "debug_logging", Boolean.FALSE)) {
			LifepathMod.LOGGER.info("ability {} -> {}", def.id(), outcome);
		}
		return outcome;
	}

	/** Kept for existing call sites/tests — the key format lives in {@link CooldownService}. */
	static ResourceLocation scheduleKey(ResourceLocation abilityId) {
		return CooldownService.scheduleKey(abilityId);
	}

	private static int passiveIntervalTicks() {
		return ((Number) LifepathConfig.getOrDefault(
				LifepathMod.id("abilities"), "passive_interval_ticks", 20)).intValue();
	}

	/**
	 * Master switch ({@code abilities.toml enabled}): gates the passive sweep,
	 * event dispatch, activation, and damage modifiers — an operator kill
	 * switch that leaves character state untouched.
	 */
	public static boolean abilitiesEnabled() {
		return (Boolean) LifepathConfig.getOrDefault(
				LifepathMod.id("abilities"), "enabled", Boolean.TRUE);
	}
}
