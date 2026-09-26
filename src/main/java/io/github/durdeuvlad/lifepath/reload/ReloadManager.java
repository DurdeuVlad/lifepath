package io.github.durdeuvlad.lifepath.reload;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * One reload lifecycle entrypoint for all engine/data systems. Systems register
 * via {@link #register(Identifier, Runnable)}; {@link #reloadAll()} runs every
 * registered reloader in registration order. A failing reloader is logged as
 * ERROR and skipped — reload never aborts over one broken system.
 *
 * <p>{@link #init()} bridges Minecraft's datapack reload into this manager via a
 * Fabric {@link SimpleSynchronousResourceReloadListener} on {@code SERVER_DATA},
 * so {@code /reload} and {@code /lifepath reload} (M0-3) share one path.
 */
public final class ReloadManager {
	private static final Identifier LISTENER_ID = LifepathMod.id("engine_reload");
	private static final Map<Identifier, Runnable> RELOADERS = new LinkedHashMap<>();
	private static boolean initialized;

	private ReloadManager() {
	}

	/** Registers a named reloader. Registration order is the run order. */
	public static void register(Identifier id, Runnable reloader) {
		if (RELOADERS.put(id, reloader) != null) {
			throw new IllegalArgumentException("duplicate reloader: " + id);
		}
	}

	/** Wires the datapack reload listener. Call once during mod init. */
	public static void init() {
		if (initialized) {
			return;
		}
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public Identifier getFabricId() {
						return LISTENER_ID;
					}

					@Override
					public void reload(ResourceManager manager) {
						reloadAll();
					}
				});
		initialized = true;
	}

	/** Outcome of one registered reloader for reporting (e.g. {@code /lifepath reload}). */
	public record ReloadResult(Identifier id, boolean success, @Nullable String error) {
	}

	/**
	 * Runs every registered reloader in registration order. Called by datapack
	 * reload and {@code /lifepath reload}.
	 *
	 * @return one {@link ReloadResult} per registered reloader, in run order;
	 *         a failing reloader is logged as ERROR, marked unsuccessful, and
	 *         never aborts the rest.
	 */
	public static java.util.List<ReloadResult> reloadAll() {
		java.util.List<ReloadResult> results = new java.util.ArrayList<>();
		for (Map.Entry<Identifier, Runnable> reloader : RELOADERS.entrySet()) {
			try {
				reloader.getValue().run();
				LifepathMod.LOGGER.info("reloaded {}", reloader.getKey());
				results.add(new ReloadResult(reloader.getKey(), true, null));
			} catch (Exception e) {
				LifepathMod.LOGGER.error("reloader {} failed; continuing", reloader.getKey(), e);
				results.add(new ReloadResult(reloader.getKey(), false, String.valueOf(e.getMessage())));
			}
		}
		return java.util.List.copyOf(results);
	}

	/**
	 * Test hook: clears reloaders and the init flag. Not for production use.
	 * Does not unregister the Fabric listener if {@link #init()} ran — only use
	 * in plain unit tests where the listener was never registered.
	 */
	static void resetForTests() {
		RELOADERS.clear();
		initialized = false;
	}
}
