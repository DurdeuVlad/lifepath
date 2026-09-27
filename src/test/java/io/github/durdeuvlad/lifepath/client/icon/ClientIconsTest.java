package io.github.durdeuvlad.lifepath.client.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

/**
 * M12-1: the fallback chain — declared icon → domain placeholder → empty.
 * Exercised through the MinecraftClient-free {@code pick} seam.
 */
class ClientIconsTest {

	private static final Identifier ICON =
			Identifier.of("lifepath", "textures/gui/skill/mining.png");
	private static final Identifier PLACEHOLDER =
			Identifier.of("lifepath", "textures/gui/placeholder/skill.png");

	@Test
	void presentTextureWins() {
		assertEquals(Optional.of(ICON),
				ClientIcons.pick("skill", ICON, id -> true));
	}

	@Test
	void missingTextureFallsBackToPlaceholder() {
		assertEquals(Optional.of(PLACEHOLDER),
				ClientIcons.pick("skill", ICON,
						id -> id.equals(PLACEHOLDER)));
	}

	@Test
	void missingBothResolvesEmpty() {
		assertTrue(ClientIcons.pick("skill", ICON, id -> false).isEmpty());
	}
}
