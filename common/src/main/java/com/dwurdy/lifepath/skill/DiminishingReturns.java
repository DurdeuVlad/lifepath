package com.dwurdy.lifepath.skill;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.config.LifepathConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Diminishing returns / anti-exploit v1 (GAMEDESIGN §11.1): repeated identical
 * action signatures earn progressively less XP inside a rolling window. The
 * seam is an {@link XpModifier} at the END of the pipeline — it sees every
 * skill's awards, so all current and future XP sources inherit the curve with
 * zero content-specific code. Signatures are
 * {@link com.dwurdy.lifepath.event.ActivityEvent#repetitionSignature()
 * type|sourceId} strings — a different block, recipe, or catch is a different
 * signature, so varied play is never penalized.
 *
 * <p>Persistence: the window lives on {@link PlayerCharacterData#actionSignatures}
 * and survives relog (a relog must not reset the penalty). Timestamps are
 * pruned to the window on every record, bounding memory to the cap below.
 */
public final class DiminishingReturns {
	public static final ResourceLocation CONFIG = LifepathMod.id("diminishing");
	public static final ResourceLocation MODIFIER_ID = LifepathMod.id("diminishing_returns");
	/**
	 * In-code fallback for {@code signature_cap}; the persistence bound in
	 * {@link PlayerCharacterData} also caps decoded lists at this size, so the
	 * config key is validator-bound to it.
	 */
	static final int CAP = 4096;

	private DiminishingReturns() {}

	/**
	 * Records {@code now} under {@code signature} (pruning entries outside the
	 * rolling window) and returns the in-window count INCLUDING this action.
	 * The returned count feeds {@link #multiplierFor}.
	 */
	public static int recordAndCount(PlayerCharacterData data, String signature,
			long now, long windowMs) {
		List<Long> times = new ArrayList<>(data.actionTimestamps(signature));
		long cutoff = now - windowMs;
		times.removeIf(t -> t <= cutoff);
		if (times.size() < cap()) {
			times.add(now);
		}
		data.setActionTimestamps(signature, times);
		return times.size();
	}

	/** In-window count for {@code signature} without recording (inspect/debug). */
	public static int count(PlayerCharacterData data, String signature,
			long now, long windowMs) {
		long cutoff = now - windowMs;
		int n = 0;
		for (long t : data.actionTimestamps(signature)) {
			if (t > cutoff) {
				n++;
			}
		}
		return n;
	}

	/** Tier multiplier for an in-window count (all thresholds/rates from config). */
	public static double multiplierFor(int count) {
		int t1 = intKey("tier_1_count", 64);
		int t2 = intKey("tier_2_count", 256);
		double m1 = numKey("tier_1_multiplier", 1.0);
		double m2 = numKey("tier_2_multiplier", 0.5);
		double m3 = numKey("tier_3_multiplier", 0.1);
		if (count <= Math.min(t1, t2)) {
			return m1;
		}
		if (count <= Math.max(t1, t2)) {
			return m2;
		}
		return m3;
	}

	public static boolean enabled() {
		return (Boolean) LifepathConfig.getOrDefault(CONFIG, "enabled", Boolean.TRUE);
	}

	/** Per-signature ledger cap — {@code diminishing.toml signature_cap}. */
	static int cap() {
		return intKey("signature_cap", CAP);
	}

	public static long windowMs() {
		return (long) (numKey("window_hours", 4.0) * 3_600_000.0);
	}

	private static int intKey(String key, int def) {
		return ((Number) LifepathConfig.getOrDefault(CONFIG, key, def)).intValue();
	}

	private static double numKey(String key, double def) {
		return ((Number) LifepathConfig.getOrDefault(CONFIG, key, def)).doubleValue();
	}

	/**
	 * The pipeline hook: records the event's signature and scales the award.
	 * Registered last in {@code SkillXpService.init} so it discounts the
	 * fully-multiplied amount.
	 */
	public static double apply(XpModifier.XpContext ctx, double amount) {
		// Awards already suppressed to nothing don't consume the player's
		// window — a 0× config period must not poison tiers.
		if (!enabled() || ctx.data() == null || ctx.source() == null || amount <= 0) {
			return amount;
		}
		String sig = ctx.source().repetitionSignature();
		long eventTs = ctx.source().timestamp();
		// Dedupe router fan-out: one event routed to N awards counts once.
		// Events with timestamp 0 (data/test paths) always record.
		List<Long> times = ctx.data().actionTimestamps(sig);
		int n;
		if (eventTs != 0 && !times.isEmpty() && times.get(times.size() - 1) == eventTs) {
			n = count(ctx.data(), sig, System.currentTimeMillis(), windowMs());
		} else {
			n = recordAndCount(ctx.data(), sig,
					eventTs != 0 ? eventTs : System.currentTimeMillis(), windowMs());
		}
		double m = multiplierFor(n);
		if (m < 1.0 && (Boolean) LifepathConfig.getOrDefault(
				LifepathConfig.GENERAL, "debug_logging", Boolean.FALSE)) {
			LifepathMod.LOGGER.info("[diminishing] {} x{} -> {} for {}",
					sig, n, m, ctx.skillId());
		}
		return amount * m;
	}
}
