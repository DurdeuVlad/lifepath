package com.dwurdy.lifepath.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dwurdy.lifepath.LifepathMod;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistryBootstrapTest {

	@BeforeEach
	void reset() {
		RegistryBootstrap.resetForTests();
	}

	@Test
	void runsStepsInDeterministicRegistrationOrder() {
		List<String> ran = new ArrayList<>();
		RegistryBootstrap.register(LifepathMod.id("b_step"), () -> ran.add("b"));
		RegistryBootstrap.register(LifepathMod.id("a_step"), () -> ran.add("a"));

		RegistryBootstrap.bootstrap();

		assertEquals(List.of("b", "a"), ran);
	}

	@Test
	void failingStepDoesNotAbortBootstrap() {
		List<String> ran = new ArrayList<>();
		RegistryBootstrap.register(LifepathMod.id("broken"), () -> {
			throw new IllegalStateException("boom");
		});
		RegistryBootstrap.register(LifepathMod.id("healthy"), () -> ran.add("healthy"));

		RegistryBootstrap.bootstrap();

		assertEquals(List.of("healthy"), ran);
	}

	@Test
	void duplicateStepIdsAreRejected() {
		RegistryBootstrap.register(LifepathMod.id("same"), () -> { });
		assertThrows(IllegalArgumentException.class,
				() -> RegistryBootstrap.register(LifepathMod.id("same"), () -> { }));
	}
}
