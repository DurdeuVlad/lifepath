package io.github.durdeuvlad.lifepath.ability;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.EvalContext;
import io.github.durdeuvlad.lifepath.ability.AbilityVocabulary.TargetContext;
import io.github.durdeuvlad.lifepath.character.CharacterManager;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.content.AbilityDefinition;
import io.github.durdeuvlad.lifepath.content.SpeciesDefinition;
import io.github.durdeuvlad.lifepath.content.SpecializationDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import io.github.durdeuvlad.lifepath.network.c2s.ActivateAbilityPayload;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AbilityVocabulary.init();
		// PASSIVE: coarse engine tick (config); each ability's own
		// interval_ticks is honored via a schedule marker in the cooldown map
		// (namespaced key — ability state stays in the generic maps per spec).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK
				.register(server -> {
					BuiltinActions.onServerTick(server);
					int interval = abilitiesEnabled() ? passiveIntervalTicks() : 0;
					if (interval <= 0 || ++ticks % interval != 0) {
						return;
					}
					long now = System.currentTimeMillis();
					for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
						io.github.durdeuvlad.lifepath.perf.PerfCounters.time(
								"ability.passive_sweep", () -> {
									var data = CharacterManager.getCharacter(player);
									runPassiveSweep(data, player, now);
									// M9-1: stage advance_after_seconds rides
									// the same per-player sweep interval.
									io.github.durdeuvlad.lifepath.condition.ConditionService
											.tick(data, player, now);
								});
					}
				});
		// EVENT: one bus listener; per-event filtering keeps the domain data-driven.
		ActivityDispatcher.registerAny(event -> {
			ServerPlayerEntity player = event.player();
			if (player == null) {
				return;
			}
			var data = CharacterManager.getCharacter(player);
			handleEvent(data, player, event.type(), System.currentTimeMillis());
			// M9-1: advance_events count toward condition stage progress.
			io.github.durdeuvlad.lifepath.condition.ConditionService
					.onActivity(data, player, event, System.currentTimeMillis());
			// M9-2: event-type attunement acquisition rolls on the same event.
			io.github.durdeuvlad.lifepath.attunement.AttunementService
					.onActivity(data, player, event, System.currentTimeMillis());
		});
		// ACTIVE: server re-validates the request end-to-end.
		LifepathNetworking.onC2S(ActivateAbilityPayload.ID, (payload, ctx) ->
				ctx.server().execute(() -> {
					ServerPlayerEntity player = ctx.player();
					// The queued task can run after a disconnect — resolving a
					// character for an offline entity would leak a cache entry.
					if (ctx.server().getPlayerManager().getPlayer(player.getUuid()) == player) {
						Outcome outcome = tryActivate(player, payload.abilityId());
						if (outcome != Outcome.EXECUTED) {
							io.github.durdeuvlad.lifepath.feedback.FeedbackService
									.abilityDenied(player, payload.abilityId(),
											outcome);
						}
					}
				}));
	}

	/**
	 * The derived owned-ability id set: species passive+active refs +
	 * specialization signature refs + any trait/condition/attunement/unlock id
	 * that resolves to an ability definition. Missing defs contribute nothing.
	 */
	public static Set<Identifier> ownedAbilities(PlayerCharacterData data) {
		Set<Identifier> owned = new LinkedHashSet<>();
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
		for (Identifier id : data.traits()) {
			owned.add(id);
		}
		// M9-1: conditions resolve through the service — base + cumulative
		// stage abilities (condition ids in data are NOT ability ids).
		owned.addAll(io.github.durdeuvlad.lifepath.condition.ConditionService
				.activeAbilities(data));
		// M9-2: attunements resolve through their service too — held
		// attunement ids are not ability ids either.
		owned.addAll(io.github.durdeuvlad.lifepath.attunement.AttunementService
				.activeAbilities(data));
		for (Identifier id : data.unlocks()) {
			owned.add(id);
		}
		return owned;
	}

	/**
	 * PASSIVE sweep for one player: honors each ability's {@code interval_ticks}
	 * via a namespaced schedule marker in the cooldown map (generic state —
	 * no ability-specific fields on the model).
	 */
	static void runPassiveSweep(PlayerCharacterData data,
			@Nullable ServerPlayerEntity player, long now) {
		for (Identifier id : ownedAbilities(data)) {
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
	}

	/**
	 * EVENT path: evaluates every owned EVENT ability whose trigger lists
	 * {@code eventType}. One dispatch keeps the domain data-driven — event
	 * types are plain identifiers, never Java branches.
	 */
	static void handleEvent(PlayerCharacterData data,
			@Nullable ServerPlayerEntity player, Identifier eventType, long now) {
		if (!abilitiesEnabled()) {
			return;
		}
		for (Identifier id : ownedAbilities(data)) {
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
	public static float modifyIncomingDamage(net.minecraft.entity.Entity entity,
			net.minecraft.entity.damage.DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity player)
				|| player.getWorld().isClient()) {
			return amount;
		}
		return modifyIncomingDamage(CharacterManager.getCharacter(player), player,
				new AbilityVocabulary.DamageInfo(source.getAttacker(), source, amount),
				amount);
	}

	/** Data-path core (tests): multiplies {@code amount} per passing owned def. */
	static float modifyIncomingDamage(PlayerCharacterData data,
			@Nullable ServerPlayerEntity player, AbilityVocabulary.DamageInfo info,
			float amount) {
		if (!abilitiesEnabled()) {
			return amount;
		}
		float modified = amount;
		long now = System.currentTimeMillis();
		for (Identifier id : ownedAbilities(data)) {
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

	/** Server-side activation for a C2S request — full validation, never trusts. */
	public static Outcome tryActivate(ServerPlayerEntity player, Identifier abilityId) {
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
			@Nullable ServerPlayerEntity self, Identifier abilityId, long now) {
		if (!abilitiesEnabled()) {
			return Outcome.DISABLED;
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
			@Nullable ServerPlayerEntity self, AbilityDefinition def, long now,
			double interactionSeconds) {
		if (!def.enabled()) {
			return debug(def, Outcome.DISABLED);
		}
		EvalContext ctx = new EvalContext(self, data, now, def.id());
		if (!conditionsMet(ctx, def)) {
			return debug(def, Outcome.CONDITIONS_FAILED);
		}
		if (CooldownService.isOnCooldown(data, def.id(), now)) {
			return debug(def, Outcome.ON_COOLDOWN);
		}
		if (def.cost().isPresent()) {
			AbilityDefinition.Cost cost = def.cost().get();
			// Unmaterialized resources read as their definition's default.
			if (io.github.durdeuvlad.lifepath.resource.ResourceService
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
					&& t.entity() instanceof ServerPlayerEntity sp) {
				io.github.durdeuvlad.lifepath.character.CharacterManager.changed(sp);
			}
		}
		def.cost().ifPresent(cost ->
				io.github.durdeuvlad.lifepath.resource.ResourceService.modify(
						data, self, cost.resource(), -cost.amount(), now));
		def.cooldown().ifPresent(cd -> {
			long expiry = CooldownService.trigger(data, def.id(), cd.seconds(), now);
			if (self != null && expiry >= 0) {
				try {
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(self,
							new io.github.durdeuvlad.lifepath.network.s2c
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
			@Nullable ServerPlayerEntity self, AbilityDefinition def,
			double seconds, long now) {
		if (def.resourceInteractions().isEmpty() || seconds <= 0.0) {
			return;
		}
		for (AbilityDefinition.ResourceInteraction ri : def.resourceInteractions()) {
			io.github.durdeuvlad.lifepath.resource.ResourceService.modify(
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
	static Identifier scheduleKey(Identifier abilityId) {
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
