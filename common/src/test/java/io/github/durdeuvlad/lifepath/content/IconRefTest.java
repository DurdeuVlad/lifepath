package io.github.durdeuvlad.lifepath.content;

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
}
