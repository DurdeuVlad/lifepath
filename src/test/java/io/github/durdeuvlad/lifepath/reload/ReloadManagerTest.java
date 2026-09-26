package io.github.durdeuvlad.lifepath.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReloadManagerTest {

	@BeforeEach
	void reset() {
		ReloadManager.resetForTests();
	}

	@Test
	void reloadAllRunsReloadersInRegistrationOrder() {
		List<String> ran = new ArrayList<>();
		ReloadManager.register(LifepathMod.id("first"), () -> ran.add("first"));
		ReloadManager.register(LifepathMod.id("second"), () -> ran.add("second"));

		List<ReloadManager.ReloadResult> results = ReloadManager.reloadAll();

		assertEquals(List.of("first", "second"), ran);
		assertEquals(2, results.size());
		assertTrue(results.get(0).success());
		assertTrue(results.get(1).success());
	}

	@Test
	void failingReloaderDoesNotAbortOthers() {
		List<String> ran = new ArrayList<>();
		ReloadManager.register(LifepathMod.id("broken"), () -> {
			throw new IllegalStateException("boom");
		});
		ReloadManager.register(LifepathMod.id("healthy"), () -> ran.add("healthy"));

		List<ReloadManager.ReloadResult> results = ReloadManager.reloadAll();

		assertEquals(List.of("healthy"), ran);
		assertEquals(2, results.size());
		assertEquals(LifepathMod.id("broken"), results.get(0).id());
		assertFalse(results.get(0).success());
		assertEquals("boom", results.get(0).error());
		assertEquals(LifepathMod.id("healthy"), results.get(1).id());
		assertTrue(results.get(1).success());
	}
}
