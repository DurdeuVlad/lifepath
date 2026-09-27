package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server-resolved display strings for the M6-1 character screen
 * (server → client). Carries display names/descriptions for the receiving
 * player's own species, specialization focus, and significant ids
 * (traits/conditions/attunements) so the client never needs the content
 * registries — and so a {@code hidden} species' name never travels anywhere
 * but the owning player's client.
 *
 * <p>Strings are resolved at send time on the server; unknown or deleted
 * content degrades to the raw id path (e.g. {@code "undead"}) — the client
 * renders exactly what it receives, never mutates, never guesses.
 *
 * <p>Sent alongside {@link CharacterSyncPayload} — every sync trigger (join,
 * respawn, dimension change, mutation) therefore refreshes identity text.
 */
public record IdentitySummaryPayload(IdentityCore identity,
		List<String> specFocus,
		Map<String, List<String>> sections,
		Map<String, String> abilityNames,
		List<ResourceDisplay> resourceDisplays) implements CustomPayload {

	/** Static display info for one resource def (M6-3 HUD). */
	public record ResourceDisplay(String id, String name, double defaultValue,
			int restBandIndex, List<String> bandNames) {
		static final PacketCodec<RegistryByteBuf, ResourceDisplay> CODEC =
				PacketCodec.tuple(
						PacketCodecs.STRING, ResourceDisplay::id,
						PacketCodecs.STRING, ResourceDisplay::name,
						PacketCodecs.DOUBLE, ResourceDisplay::defaultValue,
						PacketCodecs.INTEGER, ResourceDisplay::restBandIndex,
						PacketCodecs.STRING.collect(PacketCodecs.toList()),
								ResourceDisplay::bandNames,
						ResourceDisplay::new);
	}

	/** Species/specialization identity strings ("" = unset). */
	public record IdentityCore(String speciesId, String speciesName,
			String speciesDescription, String specId, String specName) {
		private static final PacketCodec<RegistryByteBuf, IdentityCore> CORE_CODEC =
				PacketCodec.tuple(
						PacketCodecs.STRING, IdentityCore::speciesId,
						PacketCodecs.STRING, IdentityCore::speciesName,
						PacketCodecs.STRING, IdentityCore::speciesDescription,
						PacketCodecs.STRING, IdentityCore::specId,
						PacketCodecs.STRING, IdentityCore::specName,
						IdentityCore::new);
	}

	public static final CustomPayload.Id<IdentitySummaryPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/identity"));

	public static final PacketCodec<RegistryByteBuf, IdentitySummaryPayload> PACKET_CODEC =
			PacketCodec.tuple(
					IdentityCore.CORE_CODEC, IdentitySummaryPayload::identity,
					PacketCodecs.STRING.collect(PacketCodecs.toList()),
							IdentitySummaryPayload::specFocus,
					PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING,
							PacketCodecs.STRING.collect(PacketCodecs.toList())),
							IdentitySummaryPayload::sections,
					PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING,
							PacketCodecs.STRING), IdentitySummaryPayload::abilityNames,
					ResourceDisplay.CODEC.collect(PacketCodecs.toList()),
							IdentitySummaryPayload::resourceDisplays,
					IdentitySummaryPayload::new);

	/** Empty payload — used when the character has no identity content yet. */
	public static IdentitySummaryPayload empty() {
		return new IdentitySummaryPayload(
				new IdentityCore("", "", "", "", ""), List.of(), Map.of(),
				Map.of(), List.of());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
