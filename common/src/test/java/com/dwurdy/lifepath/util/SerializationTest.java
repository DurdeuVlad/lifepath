package com.dwurdy.lifepath.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SerializationTest {

	private record Sample(String name, int level, boolean flag) {
		private static final Codec<Sample> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.STRING.fieldOf("name").forGetter(Sample::name),
				Codec.INT.fieldOf("level").forGetter(Sample::level),
				Codec.BOOL.fieldOf("flag").forGetter(Sample::flag)
		).apply(instance, Sample::new));
	}

	@Test
	void nbtRoundTripPreservesValue() {
		Sample sample = new Sample("dummy", 7, true);
		assertEquals(sample, Serialization.fromNbt(Sample.CODEC, Serialization.toNbt(Sample.CODEC, sample)));
	}

	@Test
	void jsonRoundTripPreservesValue() {
		Sample sample = new Sample("dummy", 7, true);
		assertEquals(sample, Serialization.fromJson(Sample.CODEC, Serialization.toJson(Sample.CODEC, sample)));
	}

	@Test
	void putAndGetNbtRoundTrip() {
		Sample sample = new Sample("dummy", 7, true);
		CompoundTag compound = new CompoundTag();
		Serialization.putNbt(compound, "data", Sample.CODEC, sample);
		assertEquals(sample, Serialization.getNbt(compound, "data", Sample.CODEC).orElseThrow());
		assertTrue(Serialization.getNbt(compound, "missing", Sample.CODEC).isEmpty());
	}
}
