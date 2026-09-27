package io.github.durdeuvlad.lifepath.platform;

/**
 * Loader-agnostic access point for the platform seam (M13-3). Loader modules
 * install an implementation at bootstrap — {@code FabricLifepath} calls
 * {@link #init} before {@code LifepathMod.init()} — so common code never
 * imports a loader API.
 *
 * <p>Contract: {@link #get} is valid from mod init onward. Calling it earlier
 * (e.g. a static initializer) throws — that is a wiring bug, not a runtime
 * condition.
 */
public final class Platform {
	private static PlatformServices services;

	private Platform() {
	}

	/** Installs the loader's implementation. Idempotent-guarded: once set, never replaced. */
	public static void init(PlatformServices impl) {
		if (services != null) {
			throw new IllegalStateException("Platform already initialized");
		}
		services = impl;
	}

	public static PlatformServices get() {
		if (services == null) {
			throw new IllegalStateException(
					"Platform not initialized — loader entrypoint must call Platform.init first");
		}
		return services;
	}

	/** Whether a platform implementation is installed (tests install a noop stub). */
	public static boolean isInitialized() {
		return services != null;
	}
}
