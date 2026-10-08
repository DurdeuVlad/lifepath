package com.dwurdy.lifepath.compat.pehkui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Compat A5 absent-mod contract: with Pehkui not installed (the noop test
 * platform reports no foreign mods), the scale bridge must be a pure no-op —
 * no crash, and critically no Pehkui class may ever be loaded, so a pack
 * without the mod cannot even observe a {@code NoClassDefFoundError} in
 * generic paths.
 */
class PehkuiScaleTest {
	@Test
	void absentPehkuiIsASilentNoOp() {
		// null player proves the absent check happens before any dereference.
		assertDoesNotThrow(() -> PehkuiScale.setBaseScale(null, 0.5f));
		assertDoesNotThrow(() -> PehkuiScale.setBaseScale(null, 2.0f));
	}

	@Test
	void absentPehkuiNeverLoadsForeignClasses() {
		PehkuiScale.setBaseScale(null, 1.5f);
		for (String name : new String[] {
				"virtuoel.pehkui.api.ScaleTypes",
				"virtuoel.pehkui.api.ScaleType",
				"virtuoel.pehkui.api.ScaleData"}) {
			assertThrows(ClassNotFoundException.class,
					() -> Class.forName(name),
					() -> name + " must not be on the common classpath");
		}
	}
}
