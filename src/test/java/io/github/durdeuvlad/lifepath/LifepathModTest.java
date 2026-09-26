package io.github.durdeuvlad.lifepath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import org.junit.jupiter.api.Test;

class LifepathModTest {

	@Test
	void idUsesLifepathNamespace() {
		Identifier id = LifepathMod.id("species/sylvian");
		assertEquals("lifepath", id.getNamespace());
		assertEquals("species/sylvian", id.getPath());
		assertEquals("lifepath:species/sylvian", id.toString());
	}

	@Test
	void idRejectsInvalidPathCharacters() {
		assertThrows(InvalidIdentifierException.class, () -> LifepathMod.id("UPPER CASE!"));
	}
}
