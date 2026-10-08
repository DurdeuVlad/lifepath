package com.dwurdy.lifepath.network.s2c;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.network.LifepathNetworking;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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
		public record Display(Component name, Component description,
				String rankKey, String aptitude, Component improveHint,
				String icon) {
			static final StreamCodec<RegistryFriendlyByteBuf, Display> CODEC =
					StreamCodec.composite(
							ComponentSerialization.TRUSTED_STREAM_CODEC,
									Display::name,
							ComponentSerialization.TRUSTED_STREAM_CODEC,
									Display::description,
							ByteBufCodecs.STRING_UTF8, Display::rankKey,
							ByteBufCodecs.STRING_UTF8, Display::aptitude,
							ComponentSerialization.TRUSTED_STREAM_CODEC,
									Display::improveHint,
							ByteBufCodecs.STRING_UTF8, Display::icon,
							Display::new);
		}

		public record Progress(int level, double xpIn, double xpNeed,
				double xpTotal, int protectedFloor, long graceEndsEpochMs) {
			static final StreamCodec<RegistryFriendlyByteBuf, Progress> CODEC =
					StreamCodec.composite(
							ByteBufCodecs.INT, Progress::level,
							ByteBufCodecs.DOUBLE, Progress::xpIn,
							ByteBufCodecs.DOUBLE, Progress::xpNeed,
							ByteBufCodecs.DOUBLE, Progress::xpTotal,
							ByteBufCodecs.INT, Progress::protectedFloor,
							ByteBufCodecs.VAR_LONG, Progress::graceEndsEpochMs,
							Progress::new);
		}

		/**
		 * One roadmap row (M26 transparency): a milestone level, its flavor
		 * key, resolved effect entries, and the cumulative XP the level costs
		 * — so hover tooltips can say "what you get AND what it takes".
		 */
		public record MilestoneRow(int level, String descKey, double xpTotal,
				List<IdentitySummaryPayload.Entry> effects) {
			static final StreamCodec<RegistryFriendlyByteBuf, MilestoneRow> CODEC =
					StreamCodec.composite(
							ByteBufCodecs.INT, MilestoneRow::level,
							ByteBufCodecs.STRING_UTF8, MilestoneRow::descKey,
							ByteBufCodecs.DOUBLE, MilestoneRow::xpTotal,
							IdentitySummaryPayload.Entry.CODEC
									.apply(ByteBufCodecs.list()),
									MilestoneRow::effects,
							MilestoneRow::new);
		}

		/**
		 * One rank band's outcome odds (M26): resolved server-side from the
		 * skill's {@code outcome_rule} — yield multiplier, botch chance,
		 * quality tier ("" = unstamped), signature flag, anvil cost. Empty
		 * {@code bands} on Details = the skill has no outcome rule.
		 */
		public record BandStat(String key, int firstLevel, double outputMult,
				double failChance, double failCountMult, String qualityTier,
				boolean signItems, double anvilCostMult, double junkChance) {
			// 9 fields exceed StreamCodec.composite's arity — hand-rolled
			// encoder keeps the wire flat and the record honest.
			static final StreamCodec<RegistryFriendlyByteBuf, BandStat> CODEC =
					StreamCodec.of(
							(buf, s) -> {
								ByteBufCodecs.STRING_UTF8.encode(buf, s.key());
								ByteBufCodecs.INT.encode(buf, s.firstLevel());
								ByteBufCodecs.DOUBLE.encode(buf, s.outputMult());
								ByteBufCodecs.DOUBLE.encode(buf, s.failChance());
								ByteBufCodecs.DOUBLE.encode(buf, s.failCountMult());
								ByteBufCodecs.STRING_UTF8.encode(buf, s.qualityTier());
								ByteBufCodecs.BOOL.encode(buf, s.signItems());
								ByteBufCodecs.DOUBLE.encode(buf, s.anvilCostMult());
								ByteBufCodecs.DOUBLE.encode(buf, s.junkChance());
							},
							buf -> new BandStat(
									ByteBufCodecs.STRING_UTF8.decode(buf),
									ByteBufCodecs.INT.decode(buf),
									ByteBufCodecs.DOUBLE.decode(buf),
									ByteBufCodecs.DOUBLE.decode(buf),
									ByteBufCodecs.DOUBLE.decode(buf),
									ByteBufCodecs.STRING_UTF8.decode(buf),
									ByteBufCodecs.BOOL.decode(buf),
									ByteBufCodecs.DOUBLE.decode(buf),
									ByteBufCodecs.DOUBLE.decode(buf)));
		}

		/**
		 * {@code nextMilestoneEffects} (M16): the upcoming milestone's effect
		 * refs resolved server-side (name + icon), so the client can show
		 * "what you unlock", not just flavor text. Empty when no milestone
		 * is ahead.
		 *
		 * <p>{@code roadmap} (M26): every milestone row for the skill, sorted
		 * by level — powers the hoverable level ladder. {@code bands}: the
		 * skill's per-band outcome odds (empty = no outcome rule);
		 * {@code bandThresholds}: the 7 server-side rank-band start levels so
		 * the client names bands without trusting its local config.
		 */
		public record Details(int nextMilestoneLevel, String nextMilestoneText,
				List<IdentitySummaryPayload.Entry> bonuses,
				List<IdentitySummaryPayload.Entry> nextMilestoneEffects,
				List<MilestoneRow> roadmap, List<BandStat> bands,
				List<Integer> bandThresholds) {
			// 7 fields exceed StreamCodec.composite's arity — hand-rolled.
			private static final StreamCodec<RegistryFriendlyByteBuf,
					List<IdentitySummaryPayload.Entry>> ENTRY_LIST =
							IdentitySummaryPayload.Entry.CODEC
									.apply(ByteBufCodecs.list());
			private static final StreamCodec<RegistryFriendlyByteBuf,
					List<MilestoneRow>> ROW_LIST =
							MilestoneRow.CODEC.apply(ByteBufCodecs.list());
			private static final StreamCodec<RegistryFriendlyByteBuf,
					List<BandStat>> BAND_LIST =
							BandStat.CODEC.apply(ByteBufCodecs.list());
			private static final StreamCodec<io.netty.buffer.ByteBuf,
					List<Integer>> INT_LIST =
							ByteBufCodecs.INT.apply(ByteBufCodecs.list());

			static final StreamCodec<RegistryFriendlyByteBuf, Details> CODEC =
					StreamCodec.of(
							(buf, d) -> {
								ByteBufCodecs.INT.encode(buf, d.nextMilestoneLevel());
								ByteBufCodecs.STRING_UTF8.encode(buf, d.nextMilestoneText());
								ENTRY_LIST.encode(buf, d.bonuses());
								ENTRY_LIST.encode(buf, d.nextMilestoneEffects());
								ROW_LIST.encode(buf, d.roadmap());
								BAND_LIST.encode(buf, d.bands());
								INT_LIST.encode(buf, d.bandThresholds());
							},
							buf -> new Details(
									ByteBufCodecs.INT.decode(buf),
									ByteBufCodecs.STRING_UTF8.decode(buf),
									ENTRY_LIST.decode(buf),
									ENTRY_LIST.decode(buf),
									ROW_LIST.decode(buf),
									BAND_LIST.decode(buf),
									INT_LIST.decode(buf)));
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
