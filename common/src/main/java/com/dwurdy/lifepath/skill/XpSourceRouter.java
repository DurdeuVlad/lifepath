package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.content.XpSourceDefinition;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Consumes {@link ActivityEvent}s from the {@link ActivityDispatcher} and
 * turns them into XP awards via data-defined {@link XpSourceDefinition}s —
 * the "events resolve to XP awards through data-defined source mappings" half
 * of M2-3. Contains ZERO gameplay knowledge: matching rules live in the
 * definition files.
 *
 * <p>Flow: dispatcher → {@link #plan} (pure matching) → sink apply. The
 * production sink calls {@link SkillXpService#awardXp}; events without a
 * player produce a plan but no award (ambient events are still observable).
 */
public final class XpSourceRouter {
	private XpSourceRouter() {
	}

	/** One planned award: which skill, how much base XP. */
	public record Award(ResourceLocation skillId, ResourceLocation sourceDefId, double amount) {
	}

	/** The award sink — production wires SkillXpService; tests can substitute. */
	public interface AwardSink {
		void award(@Nullable ServerPlayer player, ResourceLocation skillId,
				double amount, ActivityEvent source);
	}

	private static AwardSink sink = (player, skillId, amount, source) -> {
		if (player != null) {
			SkillXpService.awardXp(player, skillId, amount, source);
		}
	};
	// Stable instance — method refs aren't interned, so unregister must use this field.
	private static final ActivityDispatcher.Listener ROUTER_LISTENER = XpSourceRouter::onActivity;
	private static boolean initialized;

	/** Subscribes to the dispatcher. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ActivityDispatcher.registerAny(ROUTER_LISTENER);
	}

	/** Dispatcher callback: plan then apply via the sink. Each award is isolated —
	 * one failing award must not drop the rest of the event's plan. */
	public static void onActivity(ActivityEvent event) {
		for (Award award : plan(event)) {
			try {
				sink.award(event.player(), award.skillId(), award.amount(), event);
			} catch (Exception e) {
				LifepathMod.LOGGER.error("xp source {} award failed for {}", award.sourceDefId(), event.type(), e);
			}
		}
	}

	/**
	 * Pure matching: every loaded xp_source def that matches the event yields
	 * an award entry. No side effects — exposed for tests and diagnostics.
	 */
	public static List<Award> plan(ActivityEvent event) {
		boolean allowUnmapped = (Boolean) com.dwurdy.lifepath.config.LifepathConfig
				.getOrDefault(LifepathMod.id("skills"), "unmapped_sources_award_xp", Boolean.TRUE);
		List<Award> awards = new ArrayList<>();
		for (XpSourceDefinition def : LifepathContent.xpSources().all().values()) {
			if (def.matches(event)) {
				XpSourceDefinition.Resolved resolved = def.resolve(event.sourceId(), event.tags());
				double amount = resolved.amount();
				if (amount > 0 && !def.conditionalBonuses().isEmpty()) {
					amount = applyBonuses(def, event.tags(), ctxFor(event), amount);
				}
				// Unmapped events (no exact/tag match) can be config-disabled.
				if (amount > 0 && (resolved.specific() || allowUnmapped)) {
					awards.add(new Award(def.skill(), def.id(), amount));
				}
			}
		}
		return awards;
	}

	private static com.dwurdy.lifepath.ability.AbilityVocabulary.EvalContext ctxFor(ActivityEvent event) {
		ServerPlayer p = event.player();
		return new com.dwurdy.lifepath.ability.AbilityVocabulary.EvalContext(p,
				p != null ? com.dwurdy.lifepath.character.CharacterManager.getCharacter(p) : null,
				System.currentTimeMillis());
	}

	private static final java.util.Set<String> WARNED_BONUSES =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Conditional-bonus pass: the first bonus whose {@code tag} is on the
	 * event AND whose {@code when} condition holds multiplies the amount.
	 * The condition vocabulary is the ability one ({@code player_faction},
	 * {@code has_condition}, …) — player-state gating without Java. Fails
	 * closed per source/condition pair with one warning. Playerless
	 * contexts reach conditions too — playerless-evaluable nodes like
	 * {@code has_condition} can still pass.
	 */
	static double applyBonuses(XpSourceDefinition def, java.util.Set<ResourceLocation> eventTags,
			com.dwurdy.lifepath.ability.AbilityVocabulary.EvalContext ctx, double amount) {
		for (XpSourceDefinition.ConditionalBonus b : def.conditionalBonuses()) {
			if (!eventTags.contains(b.tag())) {
				continue;
			}
			var eval = com.dwurdy.lifepath.ability.AbilityVocabulary.condition(b.when().type());
			String warnKey = def.id() + "|" + b.when().type();
			if (eval == null) {
				if (WARNED_BONUSES.add(warnKey)) {
					LifepathMod.LOGGER.warn("xp source {} bonus references unknown condition '{}' — skipped",
							def.id(), b.when().type());
				}
				continue;
			}
			try {
				if (eval.test(ctx, b.when().raw())) {
					return amount * b.multiplier();
				}
			} catch (Throwable t) {
				if (WARNED_BONUSES.add(warnKey)) {
					LifepathMod.LOGGER.warn("xp source {} bonus condition '{}' threw — skipped ({})",
							def.id(), b.when().type(), t.getMessage());
				}
			}
		}
		return amount;
	}

	/** Test hook: swap the sink. Always pair with {@link #resetForTests()}. */
	static void setSinkForTests(AwardSink testSink) {
		sink = testSink;
	}

	/**
	 * Test hook: unsubscribes from the dispatcher, restores the default sink,
	 * resets init. Self-contained — no dispatcher reset needed before re-init.
	 */
	static void resetForTests() {
		ActivityDispatcher.unregisterAny(ROUTER_LISTENER);
		sink = (player, skillId, amount, source) -> {
			if (player != null) {
				SkillXpService.awardXp(player, skillId, amount, source);
			}
		};
		initialized = false;
	}
}
