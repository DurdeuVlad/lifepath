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
 * <p>M12-1: every displayable reference rides in an {@link Entry}
 * ({@code id + name + icon}) — {@code icon} is the normalized texture id
 * resolved from the content def's {@code icon} field, or {@code ""} when the
 * def declares none. The client falls back to placeholders per
 * {@code ClientIcons}; a hidden species' icon never leaves the owner's
 * payload either.
 *
 * <p>Sent alongside {@link CharacterSyncPayload} — every sync trigger (join,
 * respawn, dimension change, mutation) therefore refreshes identity text.
 */
public record IdentitySummaryPayload(IdentityCore identity,
		List<Entry> specFocus,
		Map<String, List<Entry>> sections,
		Map<String, Entry> abilities,
		List<ResourceDisplay> resourceDisplays) implements CustomPayload {

	/**
	 * One displayable content reference (M12-1): the content id, its
	 * server-resolved display name, and its icon texture id ("" when none).
	 */
	public record Entry(String id, String name, String icon) {
		public static final PacketCodec<RegistryByteBuf, Entry> CODEC =
				PacketCodec.tuple(
						PacketCodecs.STRING, Entry::id,
						PacketCodecs.STRING, Entry::name,
						PacketCodecs.STRING, Entry::icon,
						Entry::new);
	}

	/** Static display info for one resource def (M6-3 HUD; icon added M12-3). */
	public record ResourceDisplay(String id, String name, double defaultValue,
			int restBandIndex, List<String> bandNames, String icon) {
		static final PacketCodec<RegistryByteBuf, ResourceDisplay> CODEC =
				PacketCodec.tuple(
						PacketCodecs.STRING, ResourceDisplay::id,
						PacketCodecs.STRING, ResourceDisplay::name,
						PacketCodecs.DOUBLE, ResourceDisplay::defaultValue,
						PacketCodecs.INTEGER, ResourceDisplay::restBandIndex,
						PacketCodecs.STRING.collect(PacketCodecs.toList()),
								ResourceDisplay::bandNames,
						PacketCodecs.STRING, ResourceDisplay::icon,
						ResourceDisplay::new);
	}

	/** Species/specialization identity strings ("" = unset; icons = "" none). */
	public record IdentityCore(String speciesId, String speciesName,
			String speciesDescription, String speciesIcon,
			String specId, String specName, String specIcon) {
		// Seven strings — past PacketCodec.tuple's arity, so write it out.
		private static final PacketCodec<RegistryByteBuf, IdentityCore> CORE_CODEC =
				PacketCodec.ofStatic(
						(buf, c) -> {
							PacketCodecs.STRING.encode(buf, c.speciesId());
							PacketCodecs.STRING.encode(buf, c.speciesName());
							PacketCodecs.STRING.encode(buf, c.speciesDescription());
							PacketCodecs.STRING.encode(buf, c.speciesIcon());
							PacketCodecs.STRING.encode(buf, c.specId());
							PacketCodecs.STRING.encode(buf, c.specName());
							PacketCodecs.STRING.encode(buf, c.specIcon());
						},
						buf -> new IdentityCore(
								PacketCodecs.STRING.decode(buf),
								PacketCodecs.STRING.decode(buf),
								PacketCodecs.STRING.decode(buf),
								PacketCodecs.STRING.decode(buf),
								PacketCodecs.STRING.decode(buf),
								PacketCodecs.STRING.decode(buf),
								PacketCodecs.STRING.decode(buf)));
	}

	public static final CustomPayload.Id<IdentitySummaryPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/identity"));

	public static final PacketCodec<RegistryByteBuf, IdentitySummaryPayload> PACKET_CODEC =
			PacketCodec.tuple(
					IdentityCore.CORE_CODEC, IdentitySummaryPayload::identity,
					Entry.CODEC.collect(PacketCodecs.toList()),
							IdentitySummaryPayload::specFocus,
					PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING,
							Entry.CODEC.collect(PacketCodecs.toList())),
							IdentitySummaryPayload::sections,
					PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING,
							Entry.CODEC), IdentitySummaryPayload::abilities,
					ResourceDisplay.CODEC.collect(PacketCodecs.toList()),
							IdentitySummaryPayload::resourceDisplays,
					IdentitySummaryPayload::new);

	/** Empty payload — used when the character has no identity content yet. */
	public static IdentitySummaryPayload empty() {
		return new IdentitySummaryPayload(
				new IdentityCore("", "", "", "", "", "", ""), List.of(), Map.of(),
				Map.of(), List.of());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
