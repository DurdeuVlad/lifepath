package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C selection catalog (M14): the picker's option list — every selectable
 * species and specialization resolved server-side into display fields the
 * client can render without owning the datapack registries. Per-player:
 * {@link Entry#availability} already reflects this player's unlocks and
 * specialization state, so the screen never re-derives policy (and can't get
 * it wrong — the select request is re-validated server-side anyway).
 *
 * <p>Pushed on join (drives the auto-open onboarding screen), after a
 * successful selection, and on a {@code RequestSelectionCatalogPayload}
 * refresh — which is what the screen sends when opened, so the list is
 * always fresh at the moment of choice even after datapack reloads or
 * mid-session unlocks.
 */
public record SelectionCatalogPayload(List<Entry> species,
		List<Entry> specializations) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectionCatalogPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("selection/catalog"));

	/** One picker option: display fields resolved server-side. {@code details}
	 *  are {@link Component}s (mostly {@code text.lifepath.detail.*}
	 *  translatables) so every client renders them in its own locale —
	 *  the wire carries keys + resolved names, never formatted English. */
	public record Entry(String id, Component name, Component description,
			String icon, List<Component> details, int availability) {
		/** Player may pick this right now. */
		public static final int AVAILABLE = 0;
		/** {@code selection:"unlocked"} species whose unlock id the player lacks. */
		public static final int NEEDS_UNLOCK = 1;
		/** {@code selection:"admin_only"} — listed for visibility, never player-pickable. */
		public static final int ADMIN_ONLY = 2;
		/** Specialization already set — the first pick is one-time (M3-2). */
		public static final int ALREADY_CHOSEN = 3;

		static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, Entry::id,
						ComponentSerialization.TRUSTED_STREAM_CODEC, Entry::name,
						ComponentSerialization.TRUSTED_STREAM_CODEC,
								Entry::description,
						ByteBufCodecs.STRING_UTF8, Entry::icon,
						ComponentSerialization.TRUSTED_STREAM_CODEC
								.apply(ByteBufCodecs.list()),
						Entry::details,
						ByteBufCodecs.INT, Entry::availability,
						Entry::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectionCatalogPayload> PACKET_CODEC =
			StreamCodec.composite(
					Entry.CODEC.apply(ByteBufCodecs.list()),
					SelectionCatalogPayload::species,
					Entry.CODEC.apply(ByteBufCodecs.list()),
					SelectionCatalogPayload::specializations,
					SelectionCatalogPayload::new);

	public static SelectionCatalogPayload empty() {
		return new SelectionCatalogPayload(List.of(), List.of());
	}

	@Override
	public CustomPacketPayload.Type<SelectionCatalogPayload> type() {
		return ID;
	}
}
