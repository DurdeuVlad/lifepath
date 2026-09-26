package io.github.durdeuvlad.lifepath.skill;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.Identifier;

/**
 * Maps skill level to a named rank band (GAMEDESIGN §8). Default bands:
 * 0 Untrained, 1–19 Novice, 20–39 Apprentice, 40–59 Skilled, 60–79 Expert,
 * 80–94 Master, 95–100 Legendary.
 *
 * <p>Band boundaries are config-overridable ({@code config/lifepath/skills.toml},
 * keys {@code band_<name>} = the FIRST level of that band). An invalid
 * configuration (non-increasing thresholds or out-of-range values) degrades to
 * the defaults with one WARN — never a crash.
 *
 * <p><b>Server authority note:</b> {@link #bandFor} reads the LOCAL config on
 * whichever environment calls it. Authoritative consumers (commands, sync
 * payloads) must run it server-side; client UI may display a locally-configured
 * band that differs from the server's — a known, acceptable divergence until a
 * later milestone ships thresholds in the sync payload.
 */
public final class RankBands {
	private RankBands() {
	}

	/** Named rank bands, in ascending level order. */
	public enum RankBand {
		UNTRAINED, NOVICE, APPRENTICE, SKILLED, EXPERT, MASTER, LEGENDARY;

		/** Translation key suffix / stable id for UI. */
		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final int[] DEFAULT_THRESHOLDS = {0, 1, 20, 40, 60, 80, 95};
	private static final Identifier SKILLS_CONFIG = LifepathMod.id("skills");
	private static final String[] KEYS = {
			"band_untrained", "band_novice", "band_apprentice", "band_skilled",
			"band_expert", "band_master", "band_legendary"};
	private static final AtomicBoolean warnedInvalid = new AtomicBoolean();

	/** The band containing {@code level} (any int tolerated). */
	public static RankBand bandFor(int level) {
		int[] thresholds = thresholds();
		RankBand band = RankBand.UNTRAINED;
		for (int i = thresholds.length - 1; i >= 0; i--) {
			if (level >= thresholds[i]) {
				band = RankBand.values()[i];
				break;
			}
		}
		return band;
	}

	/** Current thresholds (config with sane fallback), one entry per band, ascending. */
	public static int[] thresholds() {
		int[] t = new int[KEYS.length];
		for (int i = 0; i < KEYS.length; i++) {
			t[i] = LifepathConfig.isLoaded(SKILLS_CONFIG)
					? LifepathConfig.getInt(SKILLS_CONFIG, KEYS[i])
					: DEFAULT_THRESHOLDS[i];
		}
		if (!valid(t)) {
			if (warnedInvalid.compareAndSet(false, true)) {
				LifepathMod.LOGGER.warn(
						"invalid rank-band thresholds in skills.toml (must be ascending 0..100, first=0); using defaults");
			}
			return DEFAULT_THRESHOLDS.clone();
		}
		warnedInvalid.set(false);
		return t;
	}

	private static boolean valid(int[] t) {
		if (t[0] != 0) {
			return false;
		}
		for (int i = 1; i < t.length; i++) {
			if (t[i] <= t[i - 1] || t[i] > 100) {
				return false;
			}
		}
		return true;
	}
}
