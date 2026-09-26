package io.github.durdeuvlad.lifepath.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

		ReloadManager.reloadAll();

		assertEquals(List.of("first", "second"), ran);
	}

	@Test
	void failingReloaderDoesNotAbortOthers() {
		List<String> ran = new ArrayList<>();
		ReloadManager.register(LifepathMod.id("broken"), () -> {
			throw new IllegalStateException("boom");
		});
		ReloadManager.register(LifepathMod.id("healthy"), () -> ran.add("healthy"));

		ReloadManager.reloadAll();

		assertEquals(List.of("healthy"), ran);
	}
}
