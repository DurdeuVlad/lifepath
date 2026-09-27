package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Per-skill display cards for the M6-2 skills screens (server → client).
 * Every field the UI renders is resolved server-side: display names,
 * description/hint text, rank-band key, progress numbers, floor, grace-end
 * timestamp, next-milestone label, and current-bonus names. The client never
 * reads content registries and never mutates state.
 *
 * <p>Decay is conveyed as raw inputs ({@link Progress#protectedFloor()},
 * {@link Progress#graceEndsEpochMs()}) so the client can phrase it in plain
 * language ("Protected for 2 days") without exposing timers or red arrows.
 */
public record SkillsSummaryPayload(List<SkillCard> skills)
		implements CustomPacketPayload {

	/** One skill's display card. */
	public record SkillCard(String id, Display display, Progress progress,
			Details details) {
		/** {@code icon} is the normalized texture id from the def's
		 * {@code icon} field, or "" when none (M12-1). */
		public record Display(String name, String description, String rankKey,
				String aptitude, String improveHint, String icon) {
			static final StreamCodec<RegistryFriendlyByteBuf, Display> CODEC =
					StreamCodec.composite(
							ByteBufCodecs.STRING_UTF8, Display::name,
							ByteBufCodecs.STRING_UTF8, Display::description,
							ByteBufCodecs.STRING_UTF8, Display::rankKey,
							ByteBufCodecs.STRING_UTF8, Display::aptitude,
							ByteBufCodecs.STRING_UTF8, Display::improveHint,
							ByteBufCodecs.STRING_UTF8, Display::icon,
							Display::new);
		}

		public record Progress(int level, double xpIn, double xpNeed,
				int protectedFloor, long graceEndsEpochMs) {
			static final StreamCodec<RegistryFriendlyByteBuf, Progress> CODEC =
					StreamCodec.composite(
							ByteBufCodecs.INT, Progress::level,
							ByteBufCodecs.DOUBLE, Progress::xpIn,
							ByteBufCodecs.DOUBLE, Progress::xpNeed,
							ByteBufCodecs.INT, Progress::protectedFloor,
							ByteBufCodecs.VAR_LONG, Progress::graceEndsEpochMs,
							Progress::new);
		}

		public record Details(int nextMilestoneLevel, String nextMilestoneText,
				List<IdentitySummaryPayload.Entry> bonuses) {
			static final StreamCodec<RegistryFriendlyByteBuf, Details> CODEC =
					StreamCodec.composite(
							ByteBufCodecs.INT, Details::nextMilestoneLevel,
							ByteBufCodecs.STRING_UTF8, Details::nextMilestoneText,
							IdentitySummaryPayload.Entry.CODEC
									.apply(ByteBufCodecs.list()),
									Details::bonuses,
							Details::new);
		}

		static final StreamCodec<RegistryFriendlyByteBuf, SkillCard> CODEC =
				StreamCodec.composite(
						ByteBufCodecs.STRING_UTF8, SkillCard::id,
						Display.CODEC, SkillCard::display,
						Progress.CODEC, SkillCard::progress,
						Details.CODEC, SkillCard::details,
						SkillCard::new);
	}

	public static final CustomPacketPayload.Type<SkillsSummaryPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/skills"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SkillsSummaryPayload> PACKET_CODEC =
			StreamCodec.composite(
					SkillCard.CODEC.apply(ByteBufCodecs.list()),
					SkillsSummaryPayload::skills,
					SkillsSummaryPayload::new);

	public static SkillsSummaryPayload empty() {
		return new SkillsSummaryPayload(List.of());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
