package com.dwurdy.lifepath.compat;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.Platform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry for {@link ExternalActivityAdapter}s. With zero adapters the
 * vanilla producers carry smithing alone — absence is silent at INFO and one
 * DEBUG line here. M7-1 adds the entrypoint discovery that feeds this.
 */
public final class ExternalAdapterRegistry {
	private ExternalAdapterRegistry() {
	}

	private static final List<ExternalActivityAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		// M7-1: third-party adapters self-describe via the "lifepath:adapter"
		// entrypoint in their fabric.mod.json — integration without Lifepath
		// referencing a single foreign class. `initialized` flips only after
		// discovery so adapters found here pass the register() gate.
		try {
			for (ExternalActivityAdapter adapter : Platform.get().entrypoints(
					"lifepath:adapter", ExternalActivityAdapter.class)) {
				register(adapter);
			}
		} catch (Throwable t) {
			LifepathMod.LOGGER.error("lifepath:adapter entrypoint discovery failed", t);
		}
		initialized = true;
		for (ExternalActivityAdapter adapter : ADAPTERS) {
			ResourceLocation id = safeId(adapter);
			try {
				adapter.register();
				LifepathMod.LOGGER.info("External activity adapter registered: {}", id);
			} catch (Throwable t) {
				// Throwable, not RuntimeException: LinkageError (adapter built
				// against a different Lifepath/mod version) is THE expected
				// failure and must never take down Lifepath init.
				LifepathMod.LOGGER.error("External activity adapter {} failed to register — skipped",
						id, t);
			}
		}
		LifepathMod.LOGGER.debug("External activity adapters registered: {}", ADAPTERS.size());
	}

	/**
	 * Register an adapter. Must be called BEFORE Lifepath init completes —
	 * late registrations are warned and dropped (they'd sit silently dead
	 * otherwise; adapter mods should register from their own entrypoint
	 * discovery, wired in M7-1).
	 */
	public static void register(ExternalActivityAdapter adapter) {
		java.util.Objects.requireNonNull(adapter, "adapter");
		if (initialized) {
			LifepathMod.LOGGER.warn("External activity adapter {} registered after init — not wired",
					safeId(adapter));
			return;
		}
		// Entrypoint instantiation can itself call register() — guard dupes.
		if (!ADAPTERS.contains(adapter)) {
			ADAPTERS.add(adapter);
		}
	}

	private static ResourceLocation safeId(ExternalActivityAdapter adapter) {
		try {
			return adapter.id();
		} catch (Throwable t) {
			return ResourceLocation.fromNamespaceAndPath("lifepath", "unknown_adapter");
		}
	}

	static List<ResourceLocation> registeredIds() {
		return ADAPTERS.stream().map(ExternalActivityAdapter::id).toList();
	}

	/** Test hook. */
	static void resetForTests() {
		ADAPTERS.clear();
		initialized = false;
	}
}
