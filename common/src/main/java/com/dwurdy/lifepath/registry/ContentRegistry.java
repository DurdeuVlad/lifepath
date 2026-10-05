package com.dwurdy.lifepath.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Generic in-memory store for one content domain (species, skills, abilities, …).
 * Keeps insertion order so iteration — and therefore anything derived from it —
 * is deterministic. {@link #clear()} supports datapack reload rebuilding.
 *
 * <p>Registries are created and populated through {@link RegistryBootstrap};
 * this class is deliberately free of content-domain knowledge.
 */
public final class ContentRegistry<T> {
	private final ResourceLocation id;
	private final Map<ResourceLocation, T> entries = new LinkedHashMap<>();

	public ContentRegistry(ResourceLocation id) {
		this.id = id;
	}

	public ResourceLocation id() {
		return id;
	}

	public T register(ResourceLocation entryId, T value) {
		if (entries.containsKey(entryId)) {
			throw new IllegalArgumentException("duplicate entry '" + entryId + "' in registry " + id);
		}
		entries.put(entryId, value);
		return value;
	}

	@Nullable
	public T get(ResourceLocation entryId) {
		return entries.get(entryId);
	}

	public boolean contains(ResourceLocation entryId) {
		return entries.containsKey(entryId);
	}

	/**
	 * Snapshot copy of all entries. Live-view alternatives were rejected:
	 * registries get cleared and repopulated on datapack reload, and readers
	 * must not observe a half-rebuilt map or hit a ConcurrentModificationException.
	 */
	public Map<ResourceLocation, T> all() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
	}

	public Set<ResourceLocation> ids() {
		return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(entries.keySet()));
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
	}
}
