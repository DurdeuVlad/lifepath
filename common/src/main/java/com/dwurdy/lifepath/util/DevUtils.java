package com.dwurdy.lifepath.util;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.config.LifepathConfig;

/**
 * Dev/debug utilities. Verbose logging is enabled by either:
 * <ul>
 *   <li>the {@code LIFEPATH_DEBUG} environment variable, or</li>
 *   <li>{@code debug_logging = true} in {@code config/lifepath/general.toml}.</li>
 * </ul>
 *
 * <p>This is the documented home for engine-level test/dev helpers; systems may
 * gate extra diagnostics behind {@link #isDebug()} / {@link #debug(String, Object...)}.
 * Package-private {@code resetForTests()} hooks on engine singletons serve the
 * same purpose for unit tests.
 */
public final class DevUtils {
	public static final String DEBUG_ENV_VAR = "LIFEPATH_DEBUG";

	private static final boolean ENV_DEBUG = Boolean.parseBoolean(System.getenv(DEBUG_ENV_VAR));

	private DevUtils() {
	}

	public static boolean isDebug() {
		if (ENV_DEBUG) {
			return true;
		}
		try {
			return LifepathConfig.isLoaded(LifepathConfig.GENERAL)
					&& LifepathConfig.getBoolean(LifepathConfig.GENERAL, "debug_logging");
		} catch (RuntimeException e) {
			return false;
		}
	}

	public static void debug(String message, Object... args) {
		if (isDebug()) {
			LifepathMod.LOGGER.info("[dev] " + message, args);
		}
	}
}
