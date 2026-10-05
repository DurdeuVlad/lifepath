package com.dwurdy.lifepath.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Codec convenience helpers shared by persistence (M1) and network code.
 * Strict methods throw {@link IllegalArgumentException} on encode/decode
 * failure; the {@code get*} variants return {@link Optional} for absent data.
 */
public final class Serialization {
	private Serialization() {
	}

	public static <T> JsonElement toJson(Codec<T> codec, T value) {
		return codec.encodeStart(JsonOps.INSTANCE, value)
				.resultOrPartial(error -> {
					throw new IllegalArgumentException("json encode failed: " + error);
				})
				.orElseThrow(() -> new IllegalArgumentException("json encode failed: empty result"));
	}

	public static <T> T fromJson(Codec<T> codec, JsonElement json) {
		return codec.decode(JsonOps.INSTANCE, json)
				.resultOrPartial(error -> {
					throw new IllegalArgumentException("json decode failed: " + error);
				})
				.orElseThrow(() -> new IllegalArgumentException("json decode failed: empty result"))
				.getFirst();
	}

	public static <T> Tag toNbt(Codec<T> codec, T value) {
		return codec.encodeStart(NbtOps.INSTANCE, value)
				.resultOrPartial(error -> {
					throw new IllegalArgumentException("nbt encode failed: " + error);
				})
				.orElseThrow(() -> new IllegalArgumentException("nbt encode failed: empty result"));
	}

	public static <T> T fromNbt(Codec<T> codec, Tag nbt) {
		return codec.decode(NbtOps.INSTANCE, nbt)
				.resultOrPartial(error -> {
					throw new IllegalArgumentException("nbt decode failed: " + error);
				})
				.orElseThrow(() -> new IllegalArgumentException("nbt decode failed: empty result"))
				.getFirst();
	}

	/** Encodes {@code value} and stores it under {@code key} in {@code compound}. */
	public static <T> void putNbt(CompoundTag compound, String key, Codec<T> codec, T value) {
		compound.put(key, toNbt(codec, value));
	}

	/**
	 * Decodes the element stored under {@code key}, or empty if the key is absent.
	 *
	 * @throws IllegalArgumentException if the key exists but fails to decode —
	 *         callers loading potentially-corrupt saves should catch this.
	 */
	public static <T> Optional<T> getNbt(CompoundTag compound, String key, Codec<T> codec) {
		if (!compound.contains(key)) {
			return Optional.empty();
		}
		return Optional.of(fromNbt(codec, compound.get(key)));
	}
}
