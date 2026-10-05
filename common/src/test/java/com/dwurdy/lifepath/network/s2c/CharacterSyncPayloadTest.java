package com.dwurdy.lifepath.network.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.skill.Aptitude;
import com.dwurdy.lifepath.skill.SkillProgress;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CharacterSyncPayloadTest {

	@Test
	void packetCodecRoundTripsFullSnapshot() {
		PlayerCharacterData data = sampleData();

		// The payload encodes pure NBT — the registry manager is never consulted,
		// so EMPTY is sufficient (full Minecraft bootstrap is not needed).
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		CharacterSyncPayload.PACKET_CODEC.encode(buf, new CharacterSyncPayload(data));
		CharacterSyncPayload decoded = CharacterSyncPayload.PACKET_CODEC.decode(buf);

		assertEquals(data, decoded.snapshot());
	}

	@Test
	void payloadIdUsesSyncNamespace() {
		assertEquals(ResourceLocation.fromNamespaceAndPath("lifepath", "sync/character"), CharacterSyncPayload.ID.id());
	}

	private static PlayerCharacterData sampleData() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(ResourceLocation.fromNamespaceAndPath("lifepath", "test_species"));
		data.setSkillProgress(ResourceLocation.fromNamespaceAndPath("lifepath", "s"),
				new SkillProgress(1.0, 2, 3, 1, Aptitude.B, 9L));
		data.addId(PlayerCharacterData.ListKind.CONDITIONS, ResourceLocation.fromNamespaceAndPath("lifepath", "c1"));
		data.setCooldown(ResourceLocation.fromNamespaceAndPath("lifepath", "ab"), 5000L);
		return data;
	}
}
