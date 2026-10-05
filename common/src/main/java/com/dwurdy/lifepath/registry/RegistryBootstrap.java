package com.dwurdy.lifepath.registry;

import com.dwurdy.lifepath.LifepathMod;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Single deterministic bootstrap path for every Lifepath content registry.
 * Content systems (M1+) register a named step via {@link #register(ResourceLocation, Runnable)}
 * during mod init; {@link #bootstrap()} then runs all steps in registration order.
 * A step that throws is logged as ERROR and skipped — bootstrap never aborts over
 * one broken system (graceful degradation contract).
 */
public final class RegistryBootstrap {
	private static final Map<ResourceLocation, Runnable> STEPS = new LinkedHashMap<>();

	private RegistryBootstrap() {
	}

	/** Registers a bootstrap step. Registration order is the execution order. */
	public static void register(ResourceLocation id, Runnable step) {
		if (STEPS.put(id, step) != null) {
			throw new IllegalArgumentException("duplicate bootstrap step: " + id);
		}
	}

	/** Runs every registered step once, in registration order. Safe to call again. */
	public static void bootstrap() {
		if (STEPS.isEmpty()) {
			LifepathMod.LOGGER.info("registry bootstrap: no steps registered yet");
			return;
		}
		for (Map.Entry<ResourceLocation, Runnable> step : STEPS.entrySet()) {
			try {
				step.getValue().run();
				LifepathMod.LOGGER.info("registry bootstrap: {} done", step.getKey());
			} catch (Exception e) {
				LifepathMod.LOGGER.error("registry bootstrap step {} failed; continuing", step.getKey(), e);
			}
		}
	}

	/** Test hook: clears all registered steps. Not for production use. */
	static void resetForTests() {
		STEPS.clear();
	}
}
