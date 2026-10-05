package com.dwurdy.lifepath.reload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.LifepathMod;
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

	@Test
	void dataReloadersRunAfterPlainReloadersWithManager() {
		List<String> ran = new ArrayList<>();
		ReloadManager.registerData(LifepathMod.id("data"), manager -> ran.add("data:" + (manager != null)));
		ReloadManager.register(LifepathMod.id("plain"), () -> ran.add("plain"));

		List<ReloadManager.ReloadResult> results =
				ReloadManager.reloadAll(new net.minecraft.server.packs.resources.ResourceManager() {
					@Override
					public java.util.Set<String> getNamespaces() {
						return java.util.Set.of();
					}

					@Override
					public java.util.Optional<net.minecraft.server.packs.resources.Resource> getResource(
							net.minecraft.resources.ResourceLocation identifier) {
						return java.util.Optional.empty();
					}

					@Override
					public java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource>
							listResources(String directory, java.util.function.Predicate<net.minecraft.resources.ResourceLocation> filter) {
						return java.util.Map.of();
					}

					@Override
					public java.util.List<net.minecraft.server.packs.resources.Resource> getResourceStack(
							net.minecraft.resources.ResourceLocation identifier) {
						return java.util.List.of();
					}

					@Override
					public java.util.Map<net.minecraft.resources.ResourceLocation, java.util.List<net.minecraft.server.packs.resources.Resource>>
							listResourceStacks(String directory, java.util.function.Predicate<net.minecraft.resources.ResourceLocation> filter) {
						return java.util.Map.of();
					}

					@Override
					public java.util.stream.Stream<net.minecraft.server.packs.PackResources> listPacks() {
						return java.util.stream.Stream.empty();
					}
				});

		assertEquals(List.of("plain", "data:true"), ran);
		assertEquals(2, results.size());
		assertTrue(results.get(1).success());
	}

	@Test
	void dataReloadersReportSkippedWithoutManager() {
		List<String> ran = new ArrayList<>();
		ReloadManager.registerData(LifepathMod.id("data"), manager -> ran.add("data"));

		List<ReloadManager.ReloadResult> results = ReloadManager.reloadAll();

		assertTrue(ran.isEmpty());
		assertEquals(1, results.size());
		assertTrue(results.get(0).success());
		assertEquals("skipped: no resource manager", results.get(0).error());
	}

	@Test
	void duplicateIdAcrossReloaderKindsIsRejected() {
		ReloadManager.registerData(LifepathMod.id("dup"), manager -> {
		});

		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> ReloadManager.register(LifepathMod.id("dup"), () -> {
				}));
	}
}
