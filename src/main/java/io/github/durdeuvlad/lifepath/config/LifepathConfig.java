package io.github.durdeuvlad.lifepath.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import io.github.durdeuvlad.lifepath.LifepathMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

/**
 * Loads and validates the TOML balance/config files under {@code config/lifepath/}.
 *
 * <p>Mechanism: NightConfig {@code TomlParser}/{@code TomlWriter} (a small, dependency-free
 * TOML implementation bundled into the mod jar). One {@link ConfigSpec} is
 * registered per file via {@link #define(Identifier, ConfigSpec)}; the file name
 * is the identifier path plus {@code .toml}.
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>Missing files/keys are created with spec defaults.</li>
 *   <li>Type mismatches are coerced where possible (e.g. TOML long to int).</li>
 *   <li>Values failing validation reset to the default and log a WARN — never fatal.</li>
 *   <li>A fundamentally unparseable file is renamed to {@code <name>.toml.invalid}
 *       and regenerated from defaults; startup continues.</li>
 *   <li>{@link #reload()} re-reads every file without a restart. Reads are served
 *       from an immutable snapshot taken at load time, so a failed reload never
 *       leaves half-applied values.</li>
 * </ul>
 */
public final class LifepathConfig {
	/** The always-present base config file ({@code config/lifepath/general.toml}). */
	public static final Identifier GENERAL = LifepathMod.id("general");

	private static final Map<Identifier, ConfigSpec> SPECS = new LinkedHashMap<>();
	private static final Map<Identifier, Map<String, Object>> VALUES = new HashMap<>();
	private static Path configDir;

	private LifepathConfig() {
	}

	/** Registers the spec for {@code config/lifepath/<id.path()>.toml}. Call during mod init. */
	public static void define(Identifier fileId, ConfigSpec spec) {
		if (SPECS.put(fileId, spec) != null) {
			throw new IllegalArgumentException("duplicate config file spec: " + fileId);
		}
	}

	/** Loads all defined files from the loader config directory. */
	public static void loadAll() {
		loadAll(defaultDir());
	}

	/** Loads all defined files from {@code dir}. Exposed for tests and the reload lifecycle. */
	public static synchronized void loadAll(Path dir) {
		configDir = dir;
		Map<Identifier, Map<String, Object>> snapshot = new HashMap<>();
		for (Map.Entry<Identifier, ConfigSpec> file : SPECS.entrySet()) {
			snapshot.put(file.getKey(), loadFile(dir, file.getKey(), file.getValue()));
		}
		VALUES.clear();
		VALUES.putAll(snapshot);
	}

	/** Re-reads all config files. Registered with {@code ReloadManager} at init. */
	public static void reload() {
		loadAll();
	}

	public static boolean isLoaded(Identifier fileId) {
		return VALUES.containsKey(fileId);
	}

	/** Returns the validated value for {@code key} in {@code file}. */
	@SuppressWarnings("unchecked")
	public static <T> T get(Identifier fileId, String key) {
		Map<String, Object> file = VALUES.get(fileId);
		if (file == null) {
			throw new IllegalStateException("config file not loaded: " + fileId);
		}
		if (!file.containsKey(key)) {
			throw new IllegalArgumentException("unknown config key '" + key + "' in " + fileId);
		}
		return (T) file.get(key);
	}

	public static boolean getBoolean(Identifier fileId, String key) {
		return get(fileId, key);
	}

	public static int getInt(Identifier fileId, String key) {
		return ((Number) get(fileId, key)).intValue();
	}

	public static double getDouble(Identifier fileId, String key) {
		return ((Number) get(fileId, key)).doubleValue();
	}

	public static String getString(Identifier fileId, String key) {
		return get(fileId, key);
	}

	private static Map<String, Object> loadFile(Path dir, Identifier fileId, ConfigSpec spec) {
		Path path = dir.resolve(fileId.getPath() + ".toml");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			LifepathMod.LOGGER.error("cannot create config dir {}", dir, e);
			return defaultsOf(spec);
		}

		CommentedConfig cfg = TomlFormat.newConfig();
		if (Files.exists(path)) {
			try (var reader = Files.newBufferedReader(path)) {
				cfg = new TomlParser().parse(reader);
			} catch (ParsingException | IOException e) {
				LifepathMod.LOGGER.warn("config {} is malformed ({}); moving it aside and regenerating defaults",
						path.getFileName(), e.getMessage());
				quarantine(path);
				cfg = TomlFormat.newConfig();
			}
		}

		Map<String, Object> values = new HashMap<>();
		boolean dirty = false;
		for (ConfigSpec.Entry entry : spec.entries()) {
			Object raw = cfg.get(entry.key());
			Object value;
			if (raw == null) {
				value = entry.defaultValue();
				cfg.set(entry.key(), value);
				dirty = true;
			} else {
				value = coerce(raw, entry.defaultValue());
				if (!isValid(value, entry)) {
					LifepathMod.LOGGER.warn("config {}: key '{}' has invalid value {}, resetting to {}",
							path.getFileName(), entry.key(), raw, entry.defaultValue());
					value = entry.defaultValue();
					cfg.set(entry.key(), value);
					dirty = true;
				}
			}
			if (entry.comment() != null && cfg.getComment(entry.key()) == null) {
				cfg.setComment(entry.key(), entry.comment());
				dirty = true;
			}
			values.put(entry.key(), value);
		}
		for (String unknown : unknownKeys(cfg, spec)) {
			LifepathMod.LOGGER.warn("config {}: ignoring unknown key '{}'", path.getFileName(), unknown);
		}
		if (dirty) {
			try (var writer = Files.newBufferedWriter(path)) {
				new TomlWriter().write(cfg, writer);
			} catch (IOException e) {
				LifepathMod.LOGGER.error("failed to write config {}", path, e);
			}
		}
		return values;
	}

	private static boolean isValid(Object coerced, ConfigSpec.Entry entry) {
		return coerced != null
				&& entry.defaultValue().getClass().isInstance(coerced)
				&& entry.validator().test(coerced);
	}

	/** NightConfig types differ from authored defaults (e.g. TOML ints arrive as Long). */
	private static Object coerce(Object raw, Object defaultValue) {
		if (defaultValue.getClass().isInstance(raw)) {
			return raw;
		}
		if (raw instanceof Number number) {
			if (defaultValue instanceof Integer) return number.intValue();
			if (defaultValue instanceof Long) return number.longValue();
			if (defaultValue instanceof Double) return number.doubleValue();
			if (defaultValue instanceof Float) return number.floatValue();
			if (defaultValue instanceof Short) return number.shortValue();
			if (defaultValue instanceof Byte) return number.byteValue();
		}
		return raw;
	}

	private static java.util.List<String> unknownKeys(CommentedConfig cfg, ConfigSpec spec) {
		java.util.List<String> unknown = new java.util.ArrayList<>();
		for (var entry : cfg.entrySet()) {
			if (!spec.contains(entry.getKey())) {
				unknown.add(entry.getKey());
			}
		}
		return unknown;
	}

	private static Map<String, Object> defaultsOf(ConfigSpec spec) {
		Map<String, Object> values = new HashMap<>();
		for (ConfigSpec.Entry entry : spec.entries()) {
			values.put(entry.key(), entry.defaultValue());
		}
		return values;
	}

	private static void quarantine(Path path) {
		try {
			Files.move(path, path.resolveSibling(path.getFileName() + ".invalid"),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LifepathMod.LOGGER.error("failed to quarantine malformed config {}", path, e);
		}
	}

	private static Path defaultDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(LifepathMod.MOD_ID);
	}

	/** Test hook: clears all specs and loaded values. Not for production use. */
	static void resetForTests() {
		SPECS.clear();
		VALUES.clear();
		configDir = null;
	}
}
