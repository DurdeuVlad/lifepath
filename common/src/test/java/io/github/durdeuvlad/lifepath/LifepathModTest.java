package io.github.durdeuvlad.lifepath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LifepathModTest {

	@Test
	void idUsesLifepathNamespace() {
		ResourceLocation id = LifepathMod.id("test/dummy");
		assertEquals("lifepath", id.getNamespace());
		assertEquals("test/dummy", id.getPath());
		assertEquals("lifepath:test/dummy", id.toString());
	}

	@Test
	void idRejectsInvalidPathCharacters() {
		assertThrows(ResourceLocationException.class, () -> LifepathMod.id("UPPER CASE!"));
	}

	@Test
	void dataVersionStartsAtOne() {
		assertEquals(2, LifepathMod.DATA_VERSION);
	}
}
