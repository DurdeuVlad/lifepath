package com.dwurdy.lifepath.client.platform;

/**
 * Client-side counterpart of {@code Platform}: loader modules install the
 * implementation before {@code LifepathClient.init()} runs. Lives in the
 * client sourceset — the dedicated server never class-loads it.
 */
public final class ClientPlatform {
	private static ClientPlatformServices services;

	private ClientPlatform() {
	}

	public static void init(ClientPlatformServices impl) {
		if (services != null) {
			throw new IllegalStateException("ClientPlatform already initialized");
		}
		services = impl;
	}

	public static ClientPlatformServices get() {
		if (services == null) {
			throw new IllegalStateException(
					"ClientPlatform not initialized — client entrypoint must call ClientPlatform.init first");
		}
		return services;
	}

	/** Whether a client platform implementation is installed (tests install a noop stub). */
	public static boolean isInitialized() {
		return services != null;
	}
}
