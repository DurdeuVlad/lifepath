package io.github.durdeuvlad.lifepath.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

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

	public static <T> NbtElement toNbt(Codec<T> codec, T value) {
		return codec.encodeStart(NbtOps.INSTANCE, value)
				.resultOrPartial(error -> {
					throw new IllegalArgumentException("nbt encode failed: " + error);
				})
				.orElseThrow(() -> new IllegalArgumentException("nbt encode failed: empty result"));
	}

	public static <T> T fromNbt(Codec<T> codec, NbtElement nbt) {
		return codec.decode(NbtOps.INSTANCE, nbt)
				.resultOrPartial(error -> {
					throw new IllegalArgumentException("nbt decode failed: " + error);
				})
				.orElseThrow(() -> new IllegalArgumentException("nbt decode failed: empty result"))
				.getFirst();
	}

	/** Encodes {@code value} and stores it under {@code key} in {@code compound}. */
	public static <T> void putNbt(NbtCompound compound, String key, Codec<T> codec, T value) {
		compound.put(key, toNbt(codec, value));
	}

	/** Decodes the element stored under {@code key}, or empty if the key is absent. */
	public static <T> Optional<T> getNbt(NbtCompound compound, String key, Codec<T> codec) {
		if (!compound.contains(key)) {
			return Optional.empty();
		}
		return Optional.of(fromNbt(codec, compound.get(key)));
	}
}
