package io.github.durdeuvlad.lifepath.compat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalAdapterRegistryTest {
	@BeforeEach
	void setUp() {
		ExternalAdapterRegistry.resetForTests();
	}

	@Test
	void initWithNoAdaptersIsSilentAndSafe() {
		assertDoesNotThrow(ExternalAdapterRegistry::init);
		assertTrue(ExternalAdapterRegistry.registeredIds().isEmpty());
	}

	@Test
	void registeredAdapterIsInvokedOnInit() {
		AtomicBoolean called = new AtomicBoolean();
		ExternalAdapterRegistry.register(new ExternalActivityAdapter() {
			@Override
			public Identifier id() {
				return Identifier.of("example", "adapter");
			}

			@Override
			public void register() {
				called.set(true);
			}
		});
		ExternalAdapterRegistry.init();
		assertTrue(called.get());
		assertEquals(java.util.List.of(Identifier.of("example", "adapter")),
				ExternalAdapterRegistry.registeredIds());
	}

	@Test
	void failingAdapterDoesNotBlockOthers() {
		AtomicBoolean secondRan = new AtomicBoolean();
		ExternalAdapterRegistry.register(new ExternalActivityAdapter() {
			@Override
			public Identifier id() {
				return Identifier.of("example", "bad");
			}

			@Override
			public void register() {
				throw new RuntimeException("foreign mod blew up");
			}
		});
		ExternalAdapterRegistry.register(new ExternalActivityAdapter() {
			@Override
			public Identifier id() {
				return Identifier.of("example", "good");
			}

			@Override
			public void register() {
				secondRan.set(true);
			}
		});
		ExternalAdapterRegistry.init();
		assertTrue(secondRan.get(), "a throwing adapter must not block later adapters");
	}
}
