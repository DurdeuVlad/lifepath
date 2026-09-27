package io.github.durdeuvlad.lifepath.client.icon;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * M12-1: client-side resolution of content {@code icon} refs to drawable
 * textures. Resolution chain per the issue contract:
 * declared icon → per-domain placeholder glyph → empty (caller renders
 * text-only). Answers are memoized — lookups must never hit the resource
 * manager per frame.
 *
 * <p>Everything texture-side stays client-only; the shared definitions hold
 * just the normalized {@link Identifier} reference. The cache is cleared by a
 * resource-reload listener registered in {@code LifepathClient}, so F3+T or a
 * pack swap re-evaluates existence honestly.
 */
@Environment(EnvType.CLIENT)
public final class ClientIcons {
	/** Placeholder sprites ship under {@code assets/lifepath/textures/gui/placeholder/<domain>.png}. */
	private static final Map<Identifier, Optional<Identifier>> CACHE = new HashMap<>();
	private static final Set<Identifier> WARNED = new HashSet<>();

	private ClientIcons() {
	}

	/**
	 * Resolves a content def's icon to a texture that exists — the declared
	 * icon, its domain placeholder, or empty. Missing files warn once (not
	 * per frame, not per screen open).
	 */
	public static Optional<Identifier> resolve(String domain, Optional<Identifier> icon) {
		return icon.map(id -> CACHE.computeIfAbsent(id,
				key -> pick(domain, key, ClientIcons::textureExists)))
				.orElse(Optional.empty());
	}

	/**
	 * The decision, isolated from {@link MinecraftClient} so unit tests run
	 * without a client environment.
	 */
	static Optional<Identifier> pick(String domain, Identifier icon,
			Predicate<Identifier> exists) {
		if (exists.test(icon)) {
			return Optional.of(icon);
		}
		if (WARNED.add(icon)) {
			LifepathMod.LOGGER.warn("icon texture {} declared but missing — "
					+ "falling back to {} placeholder", icon, domain);
		}
		Identifier placeholder = placeholderId(domain);
		return exists.test(placeholder) ? Optional.of(placeholder) : Optional.empty();
	}

	/** Clears memoized resolutions + the warn-once set (resource reload). */
	public static void invalidate() {
		CACHE.clear();
		WARNED.clear();
	}

	private static Identifier placeholderId(String domain) {
		return Identifier.of(LifepathMod.MOD_ID,
				"textures/gui/placeholder/" + domain + ".png");
	}

	private static boolean textureExists(Identifier id) {
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.getResourceManager()
				.getResource(id).isPresent();
	}
}
