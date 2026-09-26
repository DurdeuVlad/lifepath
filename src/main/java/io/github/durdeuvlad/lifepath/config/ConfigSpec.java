package io.github.durdeuvlad.lifepath.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Declares the expected keys, defaults, validators and comments for one
 * TOML config file under {@code config/lifepath/}. Pure schema: it knows
 * nothing about file IO, which lives in {@link LifepathConfig}.
 *
 * <p>Engine rule: balance values live here, never as constants in engine code.
 */
public final class ConfigSpec {
	private final Map<String, Entry> entries;

	private ConfigSpec(Map<String, Entry> entries) {
		this.entries = Collections.unmodifiableMap(entries);
	}

	public static Builder builder() {
		return new Builder();
	}

	/** One declared config key. Validator runs on the coerced value. */
	public record Entry(String key, Object defaultValue, Predicate<Object> validator, String comment) {
		public Entry {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(defaultValue, "defaultValue");
			Objects.requireNonNull(validator, "validator");
		}
	}

	public Collection<Entry> entries() {
		return entries.values();
	}

	public boolean contains(String key) {
		return entries.containsKey(key);
	}

	public static final class Builder {
		private final Map<String, Entry> entries = new LinkedHashMap<>();

		/** Declares a key that accepts any value of the default's type. */
		public <T> Builder define(String key, T defaultValue, String comment) {
			return define(key, defaultValue, value -> true, comment);
		}

		/** Declares a validated key. Values failing validation fall back to the default with a WARN. */
		public <T> Builder define(String key, T defaultValue, Predicate<? super T> validator, String comment) {
			Objects.requireNonNull(defaultValue, "defaultValue");
			if (entries.put(key, new Entry(key, defaultValue, value -> validator.test((T) value), comment)) != null) {
				throw new IllegalArgumentException("duplicate config key: " + key);
			}
			return this;
		}

		public ConfigSpec build() {
			return new ConfigSpec(entries);
		}
	}
}
