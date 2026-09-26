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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
 *   <li>Type mismatches are coerced where lossless (e.g. TOML long to int);
 *       lossy coercions (overflow, truncation, NaN/inf) are rejected.</li>
 *   <li>Values failing validation reset to the default and log a WARN — never fatal.</li>
 *   <li>A fundamentally unparseable file is renamed to {@code <name>.toml.invalid}
 *       and regenerated from defaults; startup continues. A file that fails for
 *       any other reason falls back to spec defaults with an ERROR — one broken
 *       file never aborts {@link #loadAll(Path)}.</li>
 *   <li>{@link #reload()} re-reads every file without a restart. Reads are served
 *       from an immutable snapshot swapped in atomically at the end of a load,
 *       so a failed reload never leaves half-applied values and readers never
 *       observe a torn map.</li>
 * </ul>
 */
public final class LifepathConfig {
	/** The always-present base config file ({@code config/lifepath/general.toml}). */
	public static final Identifier GENERAL = LifepathMod.id("general");

	private static final Map<Identifier, ConfigSpec> SPECS = new LinkedHashMap<>();
	private static volatile Map<Identifier, Map<String, Object>> VALUES = Map.of();
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
			try {
				snapshot.put(file.getKey(), Map.copyOf(loadFile(dir, file.getKey(), file.getValue())));
			} catch (Exception e) {
				LifepathMod.LOGGER.error("config file {} failed to load; using defaults", file.getKey(), e);
				snapshot.put(file.getKey(), Map.copyOf(defaultsOf(file.getValue())));
			}
		}
		VALUES = Map.copyOf(snapshot);
		warnAboutUnknownFiles(dir);
	}

	/** Re-reads all config files from the directory last used by {@link #loadAll}. */
	public static void reload() {
		loadAll(configDir != null ? configDir : defaultDir());
	}

	public static boolean isLoaded(Identifier fileId) {
		return VALUES.containsKey(fileId);
	}

	/**
	 * Returns the validated value for {@code key} in {@code file}.
	 *
	 * @throws IllegalStateException    if the file's spec was never loaded
	 * @throws IllegalArgumentException if the key is not in the file's spec
	 */
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

	/**
	 * {@link #get} that never throws: returns {@code fallback} when the file
	 * isn't loaded or the key is absent. For optional/best-effort reads where a
	 * missing key must degrade gracefully rather than crash the caller.
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getOrDefault(Identifier fileId, String key, T fallback) {
		Map<String, Object> file = VALUES.get(fileId);
		if (file == null || !file.containsKey(key)) {
			return fallback;
		}
		try {
			return (T) file.get(key);
		} catch (ClassCastException e) {
			return fallback;
		}
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
			Files.createDirectories(path.getParent());
		} catch (IOException e) {
			LifepathMod.LOGGER.error("cannot create config dir {}", path.getParent(), e);
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
			} catch (Exception e) {
				LifepathMod.LOGGER.error("failed to write config {}", path, e);
			}
		}
		return values;
	}

	private static boolean isValid(Object coerced, ConfigSpec.Entry entry) {
		return coerced != null && typeMatches(coerced, entry.defaultValue())
				&& entry.validator().test(coerced);
	}

	/**
	 * Category-level type check: NightConfig returns implementation types
	 * (ArrayList, Config), so spec defaults match by interface, not concrete class.
	 */
	private static boolean typeMatches(Object value, Object defaultValue) {
		if (defaultValue.getClass().isInstance(value)) {
			return true;
		}
		if (defaultValue instanceof Number && value instanceof Number) return true;
		if (defaultValue instanceof List && value instanceof List) return true;
		return defaultValue instanceof Map && value instanceof Map;
	}

	/**
	 * NightConfig types differ from authored defaults (TOML ints arrive as Long,
	 * floats as Double). Coercion is only allowed when lossless — overflow,
	 * truncation and NaN/inf yield {@code null}, which fails validation and
	 * falls back to the default with a WARN.
	 */
	private static Object coerce(Object raw, Object defaultValue) {
		if (defaultValue.getClass().isInstance(raw)) {
			return raw;
		}
		if (!(raw instanceof Number number)) {
			return raw;
		}
		if (number instanceof Double d && !Double.isFinite(d)) return null;
		if (number instanceof Float f && !Float.isFinite(f)) return null;
		double asDouble = number.doubleValue();
		if (defaultValue instanceof Integer) {
			return isWholeWithin(asDouble, Integer.MIN_VALUE, Integer.MAX_VALUE) ? number.intValue() : null;
		}
		if (defaultValue instanceof Long) {
			return asDouble == Math.rint(asDouble) ? number.longValue() : null;
		}
		if (defaultValue instanceof Double) return asDouble;
		if (defaultValue instanceof Float) return number.floatValue();
		if (defaultValue instanceof Short) {
			return isWholeWithin(asDouble, Short.MIN_VALUE, Short.MAX_VALUE) ? number.shortValue() : null;
		}
		if (defaultValue instanceof Byte) {
			return isWholeWithin(asDouble, Byte.MIN_VALUE, Byte.MAX_VALUE) ? number.byteValue() : null;
		}
		return raw;
	}

	private static boolean isWholeWithin(double value, double min, double max) {
		return value == Math.rint(value) && value >= min && value <= max;
	}

	private static List<String> unknownKeys(CommentedConfig cfg, ConfigSpec spec) {
		List<String> unknown = new ArrayList<>();
		for (var entry : cfg.entrySet()) {
			if (!spec.contains(entry.getKey())) {
				unknown.add(entry.getKey());
			}
		}
		return unknown;
	}

	/** WARNs about {@code .toml} files that have no registered spec — usually a typo'd filename. */
	private static void warnAboutUnknownFiles(Path dir) {
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(Files::isRegularFile)
					.map(f -> f.getFileName().toString())
					.filter(name -> name.endsWith(".toml"))
					.filter(name -> SPECS.keySet().stream()
							.noneMatch(id -> (id.getPath() + ".toml").equals(name)))
					.forEach(name -> LifepathMod.LOGGER.warn(
							"config file {} has no registered spec; it is ignored", name));
		} catch (IOException ignored) {
			// Listing is best-effort diagnostics only.
		}
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
	public static void resetForTests() {
		SPECS.clear();
		VALUES = Map.of();
		configDir = null;
	}
}
