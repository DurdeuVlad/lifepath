package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.content.XpSourceDefinition;
import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.registry.LifepathContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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
	public record Award(Identifier skillId, Identifier sourceDefId, double amount) {
	}

	/** The award sink — production wires SkillXpService; tests can substitute. */
	public interface AwardSink {
		void award(@Nullable ServerPlayerEntity player, Identifier skillId,
				double amount, ActivityEvent source);
	}

	private static AwardSink sink = (player, skillId, amount, source) -> {
		if (player != null) {
			SkillXpService.awardXp(player, skillId, amount, source);
		}
	};
	private static boolean initialized;

	/** Subscribes to the dispatcher. Idempotent. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ActivityDispatcher.registerAny(XpSourceRouter::onActivity);
	}

	/** Dispatcher callback: plan then apply via the sink. */
	public static void onActivity(ActivityEvent event) {
		for (Award award : plan(event)) {
			sink.award(event.player(), award.skillId(), award.amount(), event);
		}
	}

	/**
	 * Pure matching: every loaded xp_source def that matches the event yields
	 * an award entry. No side effects — exposed for tests and diagnostics.
	 */
	public static List<Award> plan(ActivityEvent event) {
		List<Award> awards = new ArrayList<>();
		for (XpSourceDefinition def : LifepathContent.xpSources().all().values()) {
			if (def.matches(event)) {
				double amount = def.amountFor(event.sourceId());
				if (amount > 0) {
					awards.add(new Award(def.skill(), def.id(), amount));
				}
			}
		}
		return awards;
	}

	/** Test hooks: swap the sink / reset init. Not for production use. */
	static void setSinkForTests(AwardSink testSink) {
		sink = testSink;
	}

	static void resetForTests() {
		sink = (player, skillId, amount, source) -> {
			if (player != null) {
				SkillXpService.awardXp(player, skillId, amount, source);
			}
		};
		initialized = false;
	}
}
