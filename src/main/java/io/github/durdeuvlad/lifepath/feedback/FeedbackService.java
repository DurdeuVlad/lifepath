package io.github.durdeuvlad.lifepath.feedback;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.ability.AbilityEngine;
import io.github.durdeuvlad.lifepath.character.IdentitySummary;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.SkillDefinition;
import io.github.durdeuvlad.lifepath.event.SkillEvents;
import io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * M6-4 player-feedback service: server-triggered, client-rendered,
 * localized one-shot events (level-ups, milestone unlocks, identity
 * assignment, condition gain/loss, significant decay, ability denials).
 *
 * <p>Two trigger styles: discrete events ({@link SkillEvents#LEVEL_UP},
 * ability-denial results, decay passes) emit immediately; identity/condition
 * changes are detected by diffing {@link PlayerCharacterData} against the
 * last-synced copy inside {@link #onSync} — the single sync funnel in
 * {@code CharacterManager} covers every mutation path, so no caller needs
 * to remember to notify.
 */
public final class FeedbackService {
	/** What we last sent each player — the diff baseline for {@link #onSync}. */
	record Prev(Identifier speciesId, Identifier specId,
			Set<Identifier> conditions) {
		static Prev of(PlayerCharacterData d) {
			return new Prev(d.speciesId(), d.specializationId(),
					new HashSet<>(d.conditions()));
		}
	}

	/** What changed between two syncs; null fields/empty lists = no change. */
	public record SyncDiff(@Nullable Identifier speciesAssigned,
			@Nullable Identifier specAssigned, List<Identifier> conditionsGained,
			List<Identifier> conditionsLost) {
		public boolean isEmpty() {
			return speciesAssigned == null && specAssigned == null
					&& conditionsGained.isEmpty() && conditionsLost.isEmpty();
		}
	}

	private static final Map<UUID, Prev> PREVIOUS = new ConcurrentHashMap<>();
	private static boolean initialized;

	private FeedbackService() {
	}

	/** Registers the level-up listener + disconnect cleanup. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		SkillEvents.LEVEL_UP.register(FeedbackService::onLevelUp);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PREVIOUS.remove(handler.player.getUuid()));
	}

	/** Pure diff — baseline vs current data. Baseline {@code null} means
	 * first sync (join): current state becomes the baseline with no messages. */
	public static SyncDiff diff(@Nullable Prev prev, PlayerCharacterData data) {
		List<Identifier> gained = new ArrayList<>();
		List<Identifier> lost = new ArrayList<>();
		Identifier species = null, spec = null;
		if (prev == null) {
			return new SyncDiff(null, null, gained, lost);
		}
		Set<Identifier> now = new HashSet<>(data.conditions());
		for (Identifier c : now) {
			if (!prev.conditions().contains(c)) {
				gained.add(c);
			}
		}
		for (Identifier c : prev.conditions()) {
			if (!now.contains(c)) {
				lost.add(c);
			}
		}
		if (data.speciesId() != null && !data.speciesId().equals(prev.speciesId())) {
			species = data.speciesId();
		}
		if (data.specializationId() != null
				&& !data.specializationId().equals(prev.specId())) {
			spec = data.specializationId();
		}
		return new SyncDiff(species, spec, gained, lost);
	}

	/**
	 * Called from the character-sync funnel AFTER the payload sends. Emits
	 * the diff messages, then stores the new baseline.
	 */
	public static void onSync(ServerPlayerEntity player, PlayerCharacterData data) {
		if (player == null || data == null) {
			return;
		}
		SyncDiff d = diff(PREVIOUS.get(player.getUuid()), data);
		if (!d.isEmpty()) {
			if (d.speciesAssigned() != null) {
				var def = LifepathContent.species().get(d.speciesAssigned());
				send(player, "species_assigned",
						def != null ? def.displayName()
								: d.speciesAssigned().getPath());
			}
			if (d.specAssigned() != null) {
				var def = LifepathContent.specializations()
						.get(d.specAssigned());
				send(player, "spec_assigned",
						def != null ? def.displayName()
								: d.specAssigned().getPath());
			}
			for (Identifier c : d.conditionsGained()) {
				send(player, "condition_gained", IdentitySummary.displayName(c));
			}
			for (Identifier c : d.conditionsLost()) {
				send(player, "condition_lost", IdentitySummary.displayName(c));
			}
		}
		PREVIOUS.put(player.getUuid(), Prev.of(data));
	}

	/** LEVEL_UP listener — level message + any milestones crossed. */
	private static void onLevelUp(ServerPlayerEntity player, Identifier skillId,
			int oldLevel, int newLevel,
			io.github.durdeuvlad.lifepath.event.ActivityEvent source) {
		String skillName = IdentitySummary.displayName(skillId);
		send(player, "level_up", skillName, String.valueOf(newLevel));
		SkillDefinition def = LifepathContent.skills().get(skillId);
		if (def != null) {
			for (SkillDefinition.Milestone m : def.milestones()) {
				if (m.level() > oldLevel && m.level() <= newLevel) {
					send(player, "milestone", skillName,
							String.valueOf(m.level()), m.descriptionKey());
				}
			}
		}
	}

	/** "Significant decay" — a skill that lost whole levels. */
	public static void decayed(ServerPlayerEntity player, Identifier skillId,
			int levelsLost) {
		if (levelsLost > 0) {
			send(player, "decay", IdentitySummary.displayName(skillId),
					String.valueOf(levelsLost));
		}
	}

	/**
	 * Ability-activation denial with a human reason (M6-4 "denied with
	 * reason, phrased helpfully").
	 */
	public static void abilityDenied(ServerPlayerEntity player,
			Identifier abilityId, AbilityEngine.Outcome outcome) {
		String reason = switch (outcome) {
			case ON_COOLDOWN -> "cooldown";
			case CONDITIONS_FAILED -> "conditions";
			case COST_UNMET -> "cost";
			case NOT_OWNED, UNKNOWN_ABILITY -> "unavailable";
			default -> "unavailable";
		};
		long secs = io.github.durdeuvlad.lifepath.ability.CooldownService
				.remainingMillis(io.github.durdeuvlad.lifepath.character
								.CharacterManager.getCharacter(player),
						abilityId, System.currentTimeMillis()) / 1000L;
		// The AUTO sentinel reaches here only when nothing ACTIVE was owned —
		// show a plain label instead of leaking the wire id to the player.
		String name = io.github.durdeuvlad.lifepath.network.c2s
				.ActivateAbilityPayload.AUTO.equals(abilityId)
				? "Ability" : IdentitySummary.displayName(abilityId);
		send(player, "ability_denied", name, reason, String.valueOf(secs));
	}

	/**
	 * Decay pass with feedback (M6-4): runs {@code applyLazyAll} and reports
	 * each skill that lost whole levels — "significant decay". Returns the
	 * changed-count so callers keep their dirty/sync semantics.
	 */
	public static int applyDecayWithFeedback(ServerPlayerEntity player, long now) {
		var data = io.github.durdeuvlad.lifepath.character.CharacterManager
				.getCharacter(player);
		Map<Identifier, Integer> before = new java.util.HashMap<>();
		for (Map.Entry<Identifier, ?> e : data.skills().entrySet()) {
			before.put(e.getKey(),
					((io.github.durdeuvlad.lifepath.skill.SkillProgress)
							e.getValue()).level());
		}
		int changed = io.github.durdeuvlad.lifepath.skill.SkillDecayService
				.applyLazyAll(data, now);
		if (changed > 0) {
			for (Map.Entry<Identifier, Integer> e : before.entrySet()) {
				var after = data.skill(e.getKey());
				if (after != null && after.level() < e.getValue()) {
					decayed(player, e.getKey(), e.getValue() - after.level());
				}
			}
		}
		return changed;
	}

	/** Sends one feedback packet; no-ops for null/offline players. */
	public static void send(@Nullable ServerPlayerEntity player, String kind,
			String... args) {
		if (player == null) {
			return;
		}
		try {
			ServerPlayNetworking.send(player,
					new FeedbackPayload(kind, List.of(args)));
		} catch (Exception e) {
			LifepathMod.LOGGER.error("failed to send feedback {} to {}", kind,
					player.getUuid(), e);
		}
	}

	/** Test hook: drops all baselines + init flag. */
	static void resetForTests() {
		PREVIOUS.clear();
		initialized = false;
	}
}
