package io.github.durdeuvlad.lifepath.reload;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
	private static final Map<Identifier, Consumer<ResourceManager>>
			DATA_RELOADERS = new LinkedHashMap<>();
	private static boolean initialized;

	private ReloadManager() {
	}

	/** Registers a named reloader. Registration order is the run order. */
	public static void register(Identifier id, Runnable reloader) {
		if (RELOADERS.put(id, reloader) != null || DATA_RELOADERS.containsKey(id)) {
			throw new IllegalArgumentException("duplicate reloader: " + id);
		}
	}

	/**
	 * Registers a named reloader that reads from a {@link ResourceManager}
	 * (datapack content). Data reloaders run AFTER all plain reloaders, in
	 * registration order. When {@link #reloadAll()} is invoked without a
	 * resource manager (e.g. tests), data reloaders are reported as skipped
	 * rather than failed.
	 */
	public static void registerData(Identifier id,
			Consumer<ResourceManager> reloader) {
		if (DATA_RELOADERS.put(id, reloader) != null || RELOADERS.containsKey(id)) {
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
						reloadAll(manager);
					}
				});
		initialized = true;
	}

	/** Outcome of one registered reloader for reporting (e.g. {@code /lifepath reload}). */
	public record ReloadResult(Identifier id, boolean success, @Nullable String error) {
	}

	/** {@link #reloadAll(ResourceManager)} without a resource manager: data reloaders report skipped. */
	public static List<ReloadResult> reloadAll() {
		return reloadAll(null);
	}

	/**
	 * Runs every registered reloader: plain reloaders first (registration
	 * order), then data reloaders (registration order) with the given
	 * {@link ResourceManager} — the datapack bridge supplies the live one, and
	 * {@code /lifepath reload} supplies the server's. A {@code null} manager
	 * skips data reloaders (reported unsuccessful with reason "skipped").
	 */
	public static List<ReloadResult> reloadAll(@Nullable ResourceManager manager) {
		List<ReloadResult> results = new ArrayList<>();
		for (Map.Entry<Identifier, Runnable> reloader : RELOADERS.entrySet()) {
			try {
				reloader.getValue().run();
				LifepathMod.LOGGER.info("reloaded {}", reloader.getKey());
				results.add(new ReloadResult(reloader.getKey(), true, null));
			} catch (Exception e) {
				LifepathMod.LOGGER.error("reloader {} failed; continuing", reloader.getKey(), e);
				String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				results.add(new ReloadResult(reloader.getKey(), false, detail));
			}
		}
		for (Map.Entry<Identifier, Consumer<ResourceManager>> reloader : DATA_RELOADERS.entrySet()) {
			if (manager == null) {
				results.add(new ReloadResult(reloader.getKey(), true, "skipped: no resource manager"));
				continue;
			}
			try {
				reloader.getValue().accept(manager);
				LifepathMod.LOGGER.info("reloaded {}", reloader.getKey());
				results.add(new ReloadResult(reloader.getKey(), true, null));
			} catch (Exception e) {
				LifepathMod.LOGGER.error("reloader {} failed; continuing", reloader.getKey(), e);
				String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
				results.add(new ReloadResult(reloader.getKey(), false, detail));
			}
		}
		return List.copyOf(results);
	}

	/**
	 * Test hook: clears reloaders and the init flag. Not for production use.
	 * Does not unregister the Fabric listener if {@link #init()} ran — only use
	 * in plain unit tests where the listener was never registered.
	 */
	static void resetForTests() {
		RELOADERS.clear();
		DATA_RELOADERS.clear();
		initialized = false;
	}
}
