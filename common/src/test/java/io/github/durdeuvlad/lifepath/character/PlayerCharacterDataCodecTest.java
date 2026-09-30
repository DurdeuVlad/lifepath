package io.github.durdeuvlad.lifepath.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData.ResourceState;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import io.github.durdeuvlad.lifepath.util.Serialization;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PlayerCharacterDataCodecTest {

	@Test
	void defaultDataRoundTrips() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();

		Tag nbt = Serialization.toNbt(PlayerCharacterData.CODEC, data);

		assertEquals(data, Serialization.fromNbt(PlayerCharacterData.CODEC, nbt));
	}

	@Test
	void fullyPopulatedDataRoundTrips() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_species"));
		data.setSpecializationId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_spec"));
		data.setSkillProgress(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill"),
				new SkillProgress(123.5, 4, 6, 2, Aptitude.B, 1727000000000L));
		data.addId(PlayerCharacterData.ListKind.TRAITS, ResourceLocation.fromNamespaceAndPath("lifepath", "t1"));
		data.addId(PlayerCharacterData.ListKind.CONDITIONS, ResourceLocation.fromNamespaceAndPath("lifepath", "c1"));
		data.addId(PlayerCharacterData.ListKind.ATTUNEMENTS, ResourceLocation.fromNamespaceAndPath("lifepath", "a1"));
		data.addId(PlayerCharacterData.ListKind.UNLOCKS, ResourceLocation.fromNamespaceAndPath("lifepath", "u1"));
		data.setResource(ResourceLocation.fromNamespaceAndPath("lifepath", "mana"), new ResourceState(40.0, 0.0, 100.0));
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "ab1"), 999999L);
		data.setDataVersion(1);

		PlayerCharacterData decoded = Serialization.fromNbt(
				PlayerCharacterData.CODEC, Serialization.toNbt(PlayerCharacterData.CODEC, data));

		assertEquals(data, decoded);
		assertEquals(123.5, decoded.skill(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill")).xp());
		assertEquals(Aptitude.B, decoded.skill(ResourceLocation.fromNamespaceAndPath("lifepath", "test_skill")).aptitude());
	}

	@Test
	void jsonRoundTripMatchesNbtRoundTrip() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_species"));
		data.setSkillProgress(ResourceLocation.fromNamespaceAndPath("lifepath", "s"), new SkillProgress(1.0, 1, 1, 0, Aptitude.A, 7L));

		PlayerCharacterData viaJson = Serialization.fromJson(
				PlayerCharacterData.CODEC, Serialization.toJson(PlayerCharacterData.CODEC, data));

		assertEquals(data, viaJson);
	}

	@Test
	void absentSpeciesDecodesAsNull() {
		PlayerCharacterData decoded = Serialization.fromNbt(
				PlayerCharacterData.CODEC, new net.minecraft.nbt.CompoundTag());

		assertNull(decoded.speciesId());
		assertNull(decoded.specializationId());
		assertNull(decoded.morph());
		assertTrue(decoded.skills().isEmpty());
		assertTrue(decoded.traits().isEmpty());
		assertEquals(0, decoded.dataVersion());
	}

	/**
	 * M-1 (morph): the optional {@code morph} record round-trips and stays
	 * absent on pre-v3 blobs — migration-safe by codec default.
	 */
	@Test
	void morphStateRoundTrips() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setMorph(new PlayerCharacterData.MorphState(
				ResourceLocation.fromNamespaceAndPath("lifepath", "fox"), true, 1727000000000L));

		PlayerCharacterData decoded = Serialization.fromNbt(
				PlayerCharacterData.CODEC, Serialization.toNbt(PlayerCharacterData.CODEC, data));

		assertEquals(data, decoded);
		assertTrue(decoded.isMorphed());
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "fox"),
				decoded.morph().formId());
		assertEquals(1727000000000L, decoded.morph().lastMorphMs());
	}

	@Test
	void morphFieldOptionalInDecode() {
		// A morph record with only form_id decodes with inactive/epoch-0 defaults.
		net.minecraft.nbt.CompoundTag raw = new net.minecraft.nbt.CompoundTag();
		net.minecraft.nbt.CompoundTag morph = new net.minecraft.nbt.CompoundTag();
		morph.putString("form_id", "lifepath:fox");
		raw.put("morph", morph);

		PlayerCharacterData decoded = Serialization.fromNbt(PlayerCharacterData.CODEC, raw);

		org.junit.jupiter.api.Assertions.assertNotNull(decoded.morph());
		org.junit.jupiter.api.Assertions.assertFalse(decoded.isMorphed());
		assertEquals(0L, decoded.morph().lastMorphMs());
	}

	@Test
	void duplicateListEntriesAreRejected() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(LifepathMod.MOD_ID, "x");

		assertTrue(data.addId(PlayerCharacterData.ListKind.TRAITS, id));
		assertEquals(1, data.traits().size());
		org.junit.jupiter.api.Assertions.assertFalse(data.addId(PlayerCharacterData.ListKind.TRAITS, id));
	}

	@Test
	void clearExpiredCooldownsBoundary() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		ResourceLocation active = ResourceLocation.fromNamespaceAndPath("lifepath", "active");
		ResourceLocation boundary = ResourceLocation.fromNamespaceAndPath("lifepath", "boundary");
		ResourceLocation expired = ResourceLocation.fromNamespaceAndPath("lifepath", "expired");
		data.setCooldown(active, 101L);
		data.setCooldown(boundary, 100L);
		data.setCooldown(expired, 50L);

		data.clearExpiredCooldowns(100L);

		assertTrue(data.cooldowns().containsKey(active));
		org.junit.jupiter.api.Assertions.assertFalse(data.cooldowns().containsKey(boundary));
		org.junit.jupiter.api.Assertions.assertFalse(data.cooldowns().containsKey(expired));
	}
}
