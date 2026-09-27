package io.github.durdeuvlad.lifepath.network.s2c;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.network.LifepathNetworking;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

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
		implements CustomPayload {

	/** One skill's display card. */
	public record SkillCard(String id, Display display, Progress progress,
			Details details) {
		/** {@code icon} is the normalized texture id from the def's
		 * {@code icon} field, or "" when none (M12-1). */
		public record Display(String name, String description, String rankKey,
				String aptitude, String improveHint, String icon) {
			static final PacketCodec<RegistryByteBuf, Display> CODEC =
					PacketCodec.tuple(
							PacketCodecs.STRING, Display::name,
							PacketCodecs.STRING, Display::description,
							PacketCodecs.STRING, Display::rankKey,
							PacketCodecs.STRING, Display::aptitude,
							PacketCodecs.STRING, Display::improveHint,
							PacketCodecs.STRING, Display::icon,
							Display::new);
		}

		public record Progress(int level, double xpIn, double xpNeed,
				int protectedFloor, long graceEndsEpochMs) {
			static final PacketCodec<RegistryByteBuf, Progress> CODEC =
					PacketCodec.tuple(
							PacketCodecs.INTEGER, Progress::level,
							PacketCodecs.DOUBLE, Progress::xpIn,
							PacketCodecs.DOUBLE, Progress::xpNeed,
							PacketCodecs.INTEGER, Progress::protectedFloor,
							PacketCodecs.VAR_LONG, Progress::graceEndsEpochMs,
							Progress::new);
		}

		public record Details(int nextMilestoneLevel, String nextMilestoneText,
				List<IdentitySummaryPayload.Entry> bonuses) {
			static final PacketCodec<RegistryByteBuf, Details> CODEC =
					PacketCodec.tuple(
							PacketCodecs.INTEGER, Details::nextMilestoneLevel,
							PacketCodecs.STRING, Details::nextMilestoneText,
							IdentitySummaryPayload.Entry.CODEC
									.collect(PacketCodecs.toList()),
									Details::bonuses,
							Details::new);
		}

		static final PacketCodec<RegistryByteBuf, SkillCard> CODEC =
				PacketCodec.tuple(
						PacketCodecs.STRING, SkillCard::id,
						Display.CODEC, SkillCard::display,
						Progress.CODEC, SkillCard::progress,
						Details.CODEC, SkillCard::details,
						SkillCard::new);
	}

	public static final CustomPayload.Id<SkillsSummaryPayload> ID =
			LifepathNetworking.payloadId(LifepathMod.id("sync/skills"));

	public static final PacketCodec<RegistryByteBuf, SkillsSummaryPayload> PACKET_CODEC =
			PacketCodec.tuple(
					SkillCard.CODEC.collect(PacketCodecs.toList()),
					SkillsSummaryPayload::skills,
					SkillsSummaryPayload::new);

	public static SkillsSummaryPayload empty() {
		return new SkillsSummaryPayload(List.of());
	}

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
