package io.github.durdeuvlad.lifepath.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

/**
 * Generic {@code "ns:id"} or {@code "#ns:tag"} reference usable by any
 * content domain (diet rules, relation rules, …). Matching against a live
 * registry needs the entry — exact-id matching is registry-free, so headless
 * tests exercise it without bootstrapping.
 */
public record IdTagRef(@Nullable ResourceLocation exactId, @Nullable ResourceLocation tagId) {

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
			ResourceLocation tag = ResourceLocation.tryParse(raw.substring(1));
			return tag == null ? null : new IdTagRef(null, tag);
		}
		ResourceLocation id = ResourceLocation.tryParse(raw);
		return id == null ? null : new IdTagRef(id, null);
	}

	/**
	 * Does {@code id} match this ref? Exact ids match without a registry;
	 * tag refs need the target's registry entry (null entry → no match).
	 */
	public <T> boolean matches(ResourceLocation id, @Nullable Holder<T> entry,
			ResourceKey<? extends Registry<T>> registryKey) {
		if (exactId != null) {
			return exactId.equals(id);
		}
		return entry != null && tagId != null
				&& entry.is(TagKey.create(registryKey, tagId));
	}
}
