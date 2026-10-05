package com.dwurdy.lifepath.client.icon;

import com.dwurdy.lifepath.LifepathMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import com.dwurdy.lifepath.platform.ClientOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * M12-1: client-side resolution of content {@code icon} refs to drawable
 * textures. Resolution chain per the issue contract:
 * declared icon → per-domain placeholder glyph → empty (caller renders
 * text-only). Answers are memoized — lookups must never hit the resource
 * manager per frame.
 *
 * <p>Everything texture-side stays client-only; the shared definitions hold
 * just the normalized {@link ResourceLocation} reference. The cache is cleared by a
 * resource-reload listener registered in {@code LifepathClient}, so F3+T or a
 * pack swap re-evaluates existence honestly.
 */
@ClientOnly
public final class ClientIcons {
	/** Placeholder sprites ship under {@code assets/lifepath/textures/gui/placeholder/<domain>.png}. */
	private static final Map<ResourceLocation, Optional<ResourceLocation>> CACHE = new HashMap<>();
	private static final Set<ResourceLocation> WARNED = new HashSet<>();

	private ClientIcons() {
	}

	/**
	 * Resolves a content def's icon to a texture that exists — the declared
	 * icon, its domain placeholder, or empty. Missing files warn once (not
	 * per frame, not per screen open).
	 */
	public static Optional<ResourceLocation> resolve(String domain, Optional<ResourceLocation> icon) {
		return icon.map(id -> CACHE.computeIfAbsent(id,
				key -> pick(domain, key, ClientIcons::textureExists)))
				.orElse(Optional.empty());
	}

	/**
	 * String form of {@link #resolve(String, Optional)} for icon refs that
	 * arrive on the wire (payloads carry the normalized id as a string,
	 * "" = none). Malformed wire values degrade to empty — the refs were
	 * already validated server-side, so a bad string means packet corruption
	 * and is handled like "no icon" rather than warned about.
	 */
	public static Optional<ResourceLocation> resolve(String domain,
			@Nullable String iconRef) {
		if (iconRef == null || iconRef.isEmpty()) {
			return Optional.empty();
		}
		ResourceLocation id = ResourceLocation.tryParse(iconRef);
		return id != null ? resolve(domain, Optional.of(id)) : Optional.empty();
	}

	/**
	 * The decision, isolated from {@link Minecraft} so unit tests run
	 * without a client environment.
	 */
	static Optional<ResourceLocation> pick(String domain, ResourceLocation icon,
			Predicate<ResourceLocation> exists) {
		if (exists.test(icon)) {
			return Optional.of(icon);
		}
		if (WARNED.add(icon)) {
			LifepathMod.LOGGER.warn("icon texture {} declared but missing — "
					+ "falling back to {} placeholder", icon, domain);
		}
		ResourceLocation placeholder = placeholderId(domain);
		return exists.test(placeholder) ? Optional.of(placeholder) : Optional.empty();
	}

	/** Clears memoized resolutions + the warn-once set (resource reload). */
	public static void invalidate() {
		CACHE.clear();
		WARNED.clear();
	}

	private static ResourceLocation placeholderId(String domain) {
		return ResourceLocation.fromNamespaceAndPath(LifepathMod.MOD_ID,
				"textures/gui/placeholder/" + domain + ".png");
	}

	private static boolean textureExists(ResourceLocation id) {
		Minecraft client = Minecraft.getInstance();
		return client != null && client.getResourceManager()
				.getResource(id).isPresent();
	}
}
