package com.dwurdy.lifepath.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LifepathConfigTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void reset() {
		LifepathConfig.resetForTests();
	}

	private void defineGeneral() {
		LifepathConfig.define(LifepathConfig.GENERAL, ConfigSpec.builder()
				.define("debug_logging", false, "toggle")
				.define("max_level", 100, v -> v > 0, "positive cap")
				.define("label", "lifepath", "name")
				.define("ratio", 1.5, v -> v >= 0.0, "non-negative")
				.build());
	}

	@Test
	void createsDefaultsWhenMissing() {
		defineGeneral();
		LifepathConfig.loadAll(tempDir);

		assertTrue(Files.exists(tempDir.resolve("general.toml")));
		assertFalse(LifepathConfig.getBoolean(LifepathConfig.GENERAL, "debug_logging"));
		assertEquals(100, LifepathConfig.getInt(LifepathConfig.GENERAL, "max_level"));
		assertEquals("lifepath", LifepathConfig.getString(LifepathConfig.GENERAL, "label"));
		assertEquals(1.5, LifepathConfig.getDouble(LifepathConfig.GENERAL, "ratio"));
	}

	@Test
	void reloadPicksUpEditedValues() throws Exception {
		defineGeneral();
		LifepathConfig.loadAll(tempDir);

		Files.writeString(tempDir.resolve("general.toml"),
				"debug_logging = true\nmax_level = 42\nlabel = \"changed\"\nratio = 2.5\n");
		LifepathConfig.loadAll(tempDir);

		assertTrue(LifepathConfig.getBoolean(LifepathConfig.GENERAL, "debug_logging"));
		assertEquals(42, LifepathConfig.getInt(LifepathConfig.GENERAL, "max_level"));
		assertEquals("changed", LifepathConfig.getString(LifepathConfig.GENERAL, "label"));
		assertEquals(2.5, LifepathConfig.getDouble(LifepathConfig.GENERAL, "ratio"));
	}

	@Test
	void invalidValueFallsBackToDefault() throws Exception {
		defineGeneral();
		LifepathConfig.loadAll(tempDir);

		Files.writeString(tempDir.resolve("general.toml"),
				"debug_logging = \"notabool\"\nmax_level = -5\nlabel = 17\nratio = 0.5\n");
		LifepathConfig.loadAll(tempDir);

		assertFalse(LifepathConfig.getBoolean(LifepathConfig.GENERAL, "debug_logging"));
		assertEquals(100, LifepathConfig.getInt(LifepathConfig.GENERAL, "max_level"));
		assertEquals("lifepath", LifepathConfig.getString(LifepathConfig.GENERAL, "label"));
		assertEquals(0.5, LifepathConfig.getDouble(LifepathConfig.GENERAL, "ratio"));
	}

	@Test
	void tomlLongCoercesToIntDefault() throws Exception {
		defineGeneral();
		LifepathConfig.loadAll(tempDir);

		// NightConfig parses TOML integers as Long; the engine default is Integer.
		Files.writeString(tempDir.resolve("general.toml"), "max_level = 63\n");
		LifepathConfig.loadAll(tempDir);

		assertEquals(63, LifepathConfig.getInt(LifepathConfig.GENERAL, "max_level"));
	}

	@Test
	void lossyCoercionsFallBackToDefaults() throws Exception {
		defineGeneral();
		LifepathConfig.loadAll(tempDir);

		Files.writeString(tempDir.resolve("general.toml"),
				"max_level = 3000000000\nratio = nan\n");
		LifepathConfig.loadAll(tempDir);

		assertEquals(100, LifepathConfig.getInt(LifepathConfig.GENERAL, "max_level"));
		assertEquals(1.5, LifepathConfig.getDouble(LifepathConfig.GENERAL, "ratio"));
	}

	@Test
	void fractionalDoubleDoesNotCoerceToInt() throws Exception {
		defineGeneral();
		LifepathConfig.loadAll(tempDir);

		Files.writeString(tempDir.resolve("general.toml"), "max_level = 2.9\n");
		LifepathConfig.loadAll(tempDir);

		assertEquals(100, LifepathConfig.getInt(LifepathConfig.GENERAL, "max_level"));
	}

	@Test
	void dottedSpecKeysAreRejected() {
		ConfigSpec.Builder builder = ConfigSpec.builder();
		assertThrows(IllegalArgumentException.class,
				() -> builder.define("nested.key", 1, "bad"));
	}

	@Test
	void malformedFileIsQuarantinedNotFatal() throws Exception {
		defineGeneral();
		Path file = tempDir.resolve("general.toml");
		Files.writeString(file, "== not valid toml ==");

		LifepathConfig.loadAll(tempDir);

		assertTrue(Files.exists(tempDir.resolve("general.toml.invalid")));
		assertTrue(LifepathConfig.isLoaded(LifepathConfig.GENERAL));
		assertFalse(LifepathConfig.getBoolean(LifepathConfig.GENERAL, "debug_logging"));
	}

	@Test
	void unknownKeysAreIgnored() throws Exception {
		defineGeneral();
		LifepathConfig.loadAll(tempDir);

		Files.writeString(tempDir.resolve("general.toml"),
				"debug_logging = true\nsome_foreign_key = \"x\"\n");
		LifepathConfig.loadAll(tempDir);

		assertTrue(LifepathConfig.getBoolean(LifepathConfig.GENERAL, "debug_logging"));
		assertThrows(IllegalArgumentException.class,
				() -> LifepathConfig.get(LifepathConfig.GENERAL, "some_foreign_key"));
	}
}
