package io.github.durdeuvlad.lifepath.compat;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.util.Identifier;

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
		initialized = true;
		for (ExternalActivityAdapter adapter : ADAPTERS) {
			try {
				adapter.register();
				LifepathMod.LOGGER.info("External activity adapter registered: {}", adapter.id());
			} catch (RuntimeException e) {
				// A broken adapter must never take down Lifepath init.
				LifepathMod.LOGGER.error("External activity adapter {} failed to register — skipped",
						adapter.id(), e);
			}
		}
		LifepathMod.LOGGER.debug("External activity adapters registered: {}", ADAPTERS.size());
	}

	/** Register an adapter before Lifepath init completes (late calls are ignored). */
	public static void register(ExternalActivityAdapter adapter) {
		ADAPTERS.add(java.util.Objects.requireNonNull(adapter, "adapter"));
	}

	static List<Identifier> registeredIds() {
		return ADAPTERS.stream().map(ExternalActivityAdapter::id).toList();
	}

	/** Test hook. */
	static void resetForTests() {
		ADAPTERS.clear();
		initialized = false;
	}
}
