package com.dwurdy.lifepath.compat.sereneseasons;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Compat A6 absent-mod contract: with Serene Seasons not installed (the
 * noop test platform reports no foreign mods), the season bridge must be a
 * pure fail-closed no-op — and critically no foreign class may load, so a
 * pack without the mod cannot observe even a {@code NoClassDefFoundError}
 * in generic paths.
 */
class SereneSeasonsBridgeTest {
	@Test
	void absentModMatchesNothingAndNeverCrashes() {
		// null level/pos proves the absence check precedes any dereference.
		assertDoesNotThrow(() -> SereneSeasonsBridge.matches(
				null, null, Set.of("winter")));
		assertDoesNotThrow(() -> SereneSeasonsBridge.matches(
				null, null, Set.of("early_spring", "wet")));
	}

	@Test
	void absentModNeverLoadsForeignClasses() {
		SereneSeasonsBridge.matches(null, null, Set.of("summer"));
		for (String name : new String[] {
				"sereneseasons.api.season.SeasonHelper",
				"sereneseasons.api.season.ISeasonState",
				"sereneseasons.api.season.Season"}) {
			assertThrows(ClassNotFoundException.class,
					() -> Class.forName(name),
					() -> name + " must not be on the common classpath");
		}
	}
}
