package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Generic {@code "ns:id"} or {@code "#ns:tag"} reference usable by any
 * content domain (diet rules, relation rules, …). Matching against a live
 * registry needs the entry — exact-id matching is registry-free, so headless
 * tests exercise it without bootstrapping.
 */
public record IdTagRef(@Nullable Identifier exactId, @Nullable Identifier tagId) {

	public static final Codec<IdTagRef> CODEC = Codec.STRING.flatXmap(
			raw -> {
				IdTagRef ref = parse(raw);
				return ref == null
						? DataResult.error(() -> "bad id/tag ref: " + raw)
						: DataResult.success(ref);
			},
			ref -> DataResult.success(
					ref.exactId != null ? ref.exactId.toString() : "#" + ref.tagId));

	/** {@code "ns:id"} → exact, {@code "#ns:tag"} → tag. Null on malformed. */
	@Nullable
	public static IdTagRef parse(String raw) {
		if (raw == null) {
			return null;
		}
		if (raw.startsWith("#")) {
			Identifier tag = Identifier.tryParse(raw.substring(1));
			return tag == null ? null : new IdTagRef(null, tag);
		}
		Identifier id = Identifier.tryParse(raw);
		return id == null ? null : new IdTagRef(id, null);
	}

	/**
	 * Does {@code id} match this ref? Exact ids match without a registry;
	 * tag refs need the target's registry entry (null entry → no match).
	 */
	public <T> boolean matches(Identifier id, @Nullable RegistryEntry<T> entry,
			RegistryKey<? extends Registry<T>> registryKey) {
		if (exactId != null) {
			return exactId.equals(id);
		}
		return entry != null && tagId != null
				&& entry.isIn(TagKey.of(registryKey, tagId));
	}
}
