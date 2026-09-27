package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C one-shot player-feedback event (M6-4). {@code kind} selects the
 * client-side lang key {@code feedback.lifepath.<kind>} and channel
 * (chat vs actionbar); {@code args} are the translation arguments — already
 * display-resolved server-side unless the kind's contract marks an arg as a
 * lang key (e.g. milestone description keys, which the client translates).
 *
 * <p>Kinds: {@code level_up} [skillName, newLevel], {@code milestone}
 * [skillName, milestoneLevel, descriptionKey], {@code species_assigned}
 * [name], {@code spec_assigned} [name], {@code condition_gained} [name],
 * {@code condition_lost} [name], {@code decay} [skillName, levelsLost],
 * {@code ability_denied} [abilityName, reasonKey, secondsLeft],
 * {@code ability_ready} is client-generated (see {@code ClientFeedback}).
 */
public record FeedbackPayload(String kind, List<String> args) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FeedbackPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("feedback"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackPayload> PACKET_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.STRING_UTF8, FeedbackPayload::kind,
					ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
							FeedbackPayload::args,
					FeedbackPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
