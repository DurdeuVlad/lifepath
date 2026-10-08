package com.dwurdy.lifepath.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * M12-1: the {@code icon} field's normalization + leniency contract —
 * shorthand resolves under {@code lifepath:textures/gui/}, explicit
 * identifiers pass through literally, malformed input warns instead of
 * failing the file.
 */
class IconRefTest {

	@AfterEach
	void drain() {
		IconRef.drainWarnings();
	}

	@Test
	void barePathResolvesUnderGuiRoot() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lifepath", "human");
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "textures/gui/species/human.png"),
				IconRef.resolve("species", id, "species/human"));
	}

	@Test
	void barePathKeepsExistingPngSuffix() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lifepath", "mining");
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "textures/gui/skill/mining.png"),
				IconRef.resolve("skill", id, "skill/mining.png"));
	}

	@Test
	void explicitIdentifierIsLiteral() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lifepath", "human");
		// Explicit ns:path is an escape hatch — no gui-root rewriting.
		assertEquals(ResourceLocation.fromNamespaceAndPath("othermod", "icons/human.png"),
				IconRef.resolve("species", id, "othermod:icons/human.png"));
	}

	@Test
	void malformedValueWarnsAndReturnsNull() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("lifepath", "human");
		assertNull(IconRef.resolve("species", id, "not an identifier!!"));
		assertNull(IconRef.resolve("species", id, ""));
		var warnings = IconRef.drainWarnings();
		assertEquals(2, warnings.size());
		assertTrue(warnings.get(0).message().contains("not an identifier!!"));
		assertEquals("species", warnings.get(0).domain());
		assertEquals(id, warnings.get(0).file());
		assertEquals("icon", warnings.get(0).field());
		assertTrue(IconRef.drainWarnings().isEmpty());
	}

	/**
	 * Beta-10 regression: shipped {@code lifepath:} icon refs must point at
	 * real PNGs under {@code assets/lifepath/}. {@code anima_morph} and
	 * {@code hold_the_line} declared icons with no file behind them and
	 * rendered as placeholders — a missed asset is a silent UI bug, so the
	 * check walks every shipped definition's {@code icon} field.
	 */
	@Test
	void shippedLifepathIconsResolveToRealFiles() throws Exception {
		java.nio.file.Path data = java.nio.file.Path.of(
				"src/main/resources/data/lifepath");
		java.nio.file.Path assets = java.nio.file.Path.of(
				"src/main/resources/assets");
		var missing = new java.util.ArrayList<String>();
		try (var stream = java.nio.file.Files.walk(data)) {
			for (var f : stream.filter(p -> p.toString().endsWith(".json"))
					.toList()) {
				String json = java.nio.file.Files.readString(f);
				var m = java.util.regex.Pattern
						.compile("\"icon\"\\s*:\\s*\"([^\"]+)\"")
						.matcher(json);
				while (m.find()) {
					String raw = m.group(1);
					ResourceLocation rl = IconRef.resolve(
							f.getParent().getFileName().toString(),
							ResourceLocation.fromNamespaceAndPath(
									"lifepath", f.getFileName().toString()),
							raw);
					if (rl != null && rl.getNamespace().equals("lifepath")
							&& !java.nio.file.Files.exists(assets.resolve(
									rl.getNamespace()).resolve(rl.getPath()))) {
						missing.add(f.getFileName() + " -> " + rl);
					}
				}
			}
		}
		IconRef.drainWarnings();
		assertTrue(missing.isEmpty(),
				"icon refs with no texture on disk: " + missing);
	}
}
