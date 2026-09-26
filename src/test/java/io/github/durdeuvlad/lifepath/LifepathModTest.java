package io.github.durdeuvlad.lifepath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import org.junit.jupiter.api.Test;

class LifepathModTest {

	@Test
	void idUsesLifepathNamespace() {
		Identifier id = LifepathMod.id("test/dummy");
		assertEquals("lifepath", id.getNamespace());
		assertEquals("test/dummy", id.getPath());
		assertEquals("lifepath:test/dummy", id.toString());
	}

	@Test
	void idRejectsInvalidPathCharacters() {
		assertThrows(InvalidIdentifierException.class, () -> LifepathMod.id("UPPER CASE!"));
	}
}
