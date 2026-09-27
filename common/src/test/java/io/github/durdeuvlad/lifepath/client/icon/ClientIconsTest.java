package io.github.durdeuvlad.lifepath.client.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * M12-1: the fallback chain — declared icon → domain placeholder → empty.
 * Exercised through the Minecraft-free {@code pick} seam.
 */
class ClientIconsTest {

	private static final ResourceLocation ICON =
			ResourceLocation.fromNamespaceAndPath("lifepath", "textures/gui/skill/mining.png");
	private static final ResourceLocation PLACEHOLDER =
			ResourceLocation.fromNamespaceAndPath("lifepath", "textures/gui/placeholder/skill.png");

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
