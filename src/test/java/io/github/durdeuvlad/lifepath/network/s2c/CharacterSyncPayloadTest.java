package io.github.durdeuvlad.lifepath.network.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.skill.Aptitude;
import io.github.durdeuvlad.lifepath.skill.SkillProgress;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class CharacterSyncPayloadTest {

	@Test
	void packetCodecRoundTripsFullSnapshot() {
		PlayerCharacterData data = sampleData();

		// The payload encodes pure NBT — the registry manager is never consulted,
		// so EMPTY is sufficient (full Minecraft bootstrap is not needed).
		RegistryByteBuf buf = new RegistryByteBuf(
				Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		CharacterSyncPayload.PACKET_CODEC.encode(buf, new CharacterSyncPayload(data));
		CharacterSyncPayload decoded = CharacterSyncPayload.PACKET_CODEC.decode(buf);

		assertEquals(data, decoded.snapshot());
	}

	@Test
	void payloadIdUsesSyncNamespace() {
		assertEquals(Identifier.of("lifepath", "sync/character"), CharacterSyncPayload.ID.id());
	}

	private static PlayerCharacterData sampleData() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(Identifier.of("lifepath", "test_species"));
		data.setSkillProgress(Identifier.of("lifepath", "s"),
				new SkillProgress(1.0, 2, 3, 1, Aptitude.B, 9L));
		data.addId(PlayerCharacterData.ListKind.CONDITIONS, Identifier.of("lifepath", "c1"));
		data.setCooldown(Identifier.of("lifepath", "ab"), 5000L);
		return data;
	}
}
