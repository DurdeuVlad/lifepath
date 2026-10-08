package com.dwurdy.lifepath.network.s2c;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.network.LifepathNetworking;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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
		Map<String, AbilityEntry> abilities,
		List<ResourceDisplay> resourceDisplays,
		MorphView morph) implements CustomPacketPayload {

	/**
	 * One displayable content reference (M12-1): the content id, its
	 * server-resolved display name, and its icon texture id ("" when none).
	 */
	public record Entry(String id, Component name, String icon,
			Component description) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, Entry::id,
						ComponentSerialization.TRUSTED_STREAM_CODEC, Entry::name,
						ByteBufCodecs.STRING_UTF8, Entry::icon,
						ComponentSerialization.TRUSTED_STREAM_CODEC,
								Entry::description,
						Entry::new);
	}

	/**
	 * An owned ability as the client sees it: Entry fields plus whether the
	 * trigger kind is ACTIVE — the character screen needs this to offer the
	 * click-to-bind affordance only on rows the key can actually fire.
	 */
	public record AbilityEntry(String id, Component name, String icon,
			boolean active, Component description) {
		public static final StreamCodec<RegistryFriendlyByteBuf, AbilityEntry> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, AbilityEntry::id,
						ComponentSerialization.TRUSTED_STREAM_CODEC,
								AbilityEntry::name,
						ByteBufCodecs.STRING_UTF8, AbilityEntry::icon,
						ByteBufCodecs.BOOL, AbilityEntry::active,
						ComponentSerialization.TRUSTED_STREAM_CODEC,
								AbilityEntry::description,
						AbilityEntry::new);
	}

	/**
	 * Morph disguise view (morph feature M-1): everything a client needs to
	 * render the owning player's morph without owning the morph_form registry
	 * — the form id, the resolved vanilla {@code entity_type}, the display
	 * name, an icon, and whether morph is currently active. {@link #EMPTY}
	 * when the character has no morph state.
	 */
	public record MorphView(String formId, String entityType, Component name,
			String icon, boolean active) {
		public static final MorphView EMPTY =
				new MorphView("", "", Component.empty(), "", false);

		static final StreamCodec<RegistryFriendlyByteBuf, MorphView> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, MorphView::formId,
						ByteBufCodecs.STRING_UTF8, MorphView::entityType,
						ComponentSerialization.TRUSTED_STREAM_CODEC, MorphView::name,
						ByteBufCodecs.STRING_UTF8, MorphView::icon,
						ByteBufCodecs.BOOL, MorphView::active,
						MorphView::new);
	}

	/** Static display info for one resource def (M6-3 HUD; icon added M12-3). */
	public record ResourceDisplay(String id, Component name, double defaultValue,
			int restBandIndex, List<Component> bandNames, String icon) {
		static final StreamCodec<RegistryFriendlyByteBuf, ResourceDisplay> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, ResourceDisplay::id,
						ComponentSerialization.TRUSTED_STREAM_CODEC,
								ResourceDisplay::name,
						ByteBufCodecs.DOUBLE, ResourceDisplay::defaultValue,
						ByteBufCodecs.INT, ResourceDisplay::restBandIndex,
						ComponentSerialization.TRUSTED_STREAM_CODEC
								.apply(ByteBufCodecs.list()),
								ResourceDisplay::bandNames,
						ByteBufCodecs.STRING_UTF8, ResourceDisplay::icon,
						ResourceDisplay::new);
	}

	/** Species/specialization identity ("" = unset ids/icons; empty name
	 *  components = unset). Names/descriptions are components so
	 *  translatable-with-fallback keys localize per-client. */
	public record IdentityCore(String speciesId, Component speciesName,
			Component speciesDescription, String speciesIcon,
			String specId, Component specName, String specIcon,
			java.util.List<Component> speciesKit) {
		// Mixed arity — past PacketCodec.tuple's arity, so write it out.
		private static final StreamCodec<RegistryFriendlyByteBuf, IdentityCore> CORE_CODEC =
				StreamCodec.of(
						(buf, c) -> {
							ByteBufCodecs.STRING_UTF8.encode(buf, c.speciesId());
							ComponentSerialization.TRUSTED_STREAM_CODEC
									.encode(buf, c.speciesName());
							ComponentSerialization.TRUSTED_STREAM_CODEC
									.encode(buf, c.speciesDescription());
							ByteBufCodecs.STRING_UTF8.encode(buf, c.speciesIcon());
							ByteBufCodecs.STRING_UTF8.encode(buf, c.specId());
							ComponentSerialization.TRUSTED_STREAM_CODEC
									.encode(buf, c.specName());
							ByteBufCodecs.STRING_UTF8.encode(buf, c.specIcon());
							ComponentSerialization.TRUSTED_STREAM_CODEC
									.apply(ByteBufCodecs.list())
									.encode(buf, c.speciesKit());
						},
						buf -> new IdentityCore(
								ByteBufCodecs.STRING_UTF8.decode(buf),
								ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
								ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
								ByteBufCodecs.STRING_UTF8.decode(buf),
								ByteBufCodecs.STRING_UTF8.decode(buf),
								ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
								ByteBufCodecs.STRING_UTF8.decode(buf),
								ComponentSerialization.TRUSTED_STREAM_CODEC
										.apply(ByteBufCodecs.list()).decode(buf)));
	}

	public static final CustomPacketPayload.Type<IdentitySummaryPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/identity"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IdentitySummaryPayload> PACKET_CODEC =
			StreamCodec.composite(
					IdentityCore.CORE_CODEC, IdentitySummaryPayload::identity,
					Entry.CODEC.apply(ByteBufCodecs.list()),
							IdentitySummaryPayload::specFocus,
					ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8,
							Entry.CODEC.apply(ByteBufCodecs.list())),
							IdentitySummaryPayload::sections,
					// LinkedHashMap so the server's owned-set order survives the
					// wire — the character screen iterates these values directly.
					ByteBufCodecs.map(java.util.LinkedHashMap::new, ByteBufCodecs.STRING_UTF8,
							AbilityEntry.CODEC), IdentitySummaryPayload::abilities,
					ResourceDisplay.CODEC.apply(ByteBufCodecs.list()),
							IdentitySummaryPayload::resourceDisplays,
					MorphView.CODEC, IdentitySummaryPayload::morph,
					IdentitySummaryPayload::new);

	/** Empty payload — used when the character has no identity content yet. */
	public static IdentitySummaryPayload empty() {
		return new IdentitySummaryPayload(
				new IdentityCore("", Component.empty(), Component.empty(), "",
						"", Component.empty(), "", List.of()),
				List.of(), Map.of(), Map.of(), List.of(), MorphView.EMPTY);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
