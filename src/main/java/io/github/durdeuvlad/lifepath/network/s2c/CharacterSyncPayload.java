package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Full character-state snapshot sent server → client.
 *
 * <p>Carries a serialized {@link PlayerCharacterData} built by
 * {@code CharacterManager} (species/specialization ids, skill summaries —
 * level/xp/aptitude/floor/lastMeaningfulUse — traits, conditions, attunements,
 * unlocks, resources, <b>active</b> cooldowns, dataVersion). Full snapshot is
 * the chosen strategy over deltas: it is sent only on join, respawn, dimension
 * change and explicit server-side mutations — never per tick — and every map
 * is bounded by content-registry size (no unbounded history exists in the
 * model). The same Mojang codec used for persistence encodes the payload, so
 * there is a single source of truth for the field shape.
 *
 * <p>Server authority: this payload is informational only — there is no
 * corresponding C2S mutation channel, so client state can never be pushed
 * back to the server.
 */
public record CharacterSyncPayload(PlayerCharacterData snapshot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CharacterSyncPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/character"));

	private static final long MAX_SYNC_NBT_BYTES = 1_048_576L;

	public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSyncPayload> PACKET_CODEC = StreamCodec.composite(
			ByteBufCodecs.fromCodec(PlayerCharacterData.CODEC, () -> NbtAccounter.create(MAX_SYNC_NBT_BYTES)),
			CharacterSyncPayload::snapshot,
			CharacterSyncPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
