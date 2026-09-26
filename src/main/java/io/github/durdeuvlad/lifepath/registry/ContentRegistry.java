package io.github.durdeuvlad.lifepath.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Identifier;
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
	private final Identifier id;
	private final Map<Identifier, T> entries = new LinkedHashMap<>();

	public ContentRegistry(Identifier id) {
		this.id = id;
	}

	public Identifier id() {
		return id;
	}

	public T register(Identifier entryId, T value) {
		if (entries.containsKey(entryId)) {
			throw new IllegalArgumentException("duplicate entry '" + entryId + "' in registry " + id);
		}
		entries.put(entryId, value);
		return value;
	}

	@Nullable
	public T get(Identifier entryId) {
		return entries.get(entryId);
	}

	public boolean contains(Identifier entryId) {
		return entries.containsKey(entryId);
	}

	public Map<Identifier, T> all() {
		return Collections.unmodifiableMap(entries);
	}

	public Set<Identifier> ids() {
		return Collections.unmodifiableSet(entries.keySet());
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
	}
}
