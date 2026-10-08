package com.dwurdy.lifepath.network.s2c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dwurdy.lifepath.network.s2c.SkillsSummaryPayload.SkillCard;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * M26: the extended {@link SkillCard} codec — {@code MilestoneRow},
 * {@code BandStat} (hand-rolled, past composite arity), the widened
 * {@code Details} and {@code Progress#xpTotal} — must round-trip
 * byte-exactly; a field-order slip corrupts every skill screen.
 */
class SkillsSummaryPayloadTest {

	@BeforeAll
	static void bootMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void packetCodecRoundTripsRoadmapAndBandStats() {
		var entry = new IdentitySummaryPayload.Entry("lifepath:skill_mining_10",
				Component.literal("Miner's Strength I"),
				"lifepath:textures/gui/ability/m.png",
				Component.literal("+1 attack damage"));
		SkillCard card = new SkillCard("lifepath:mining",
				new SkillCard.Display(Component.literal("Mining"),
						Component.literal("Dig things."), "expert", "B",
						Component.literal("Mine ore."), "skill/mining"),
				new SkillCard.Progress(60, 12.5, 345.0, 8525.0, 30, 42L),
				new SkillCard.Details(70, "lifepath.skill.mining.milestone.70",
						List.of(entry), List.of(entry),
						List.of(new SkillCard.MilestoneRow(10,
										"lifepath.skill.mining.milestone.10",
										675.0, List.of(entry)),
								new SkillCard.MilestoneRow(95,
										"lifepath.skill.mining.milestone.95",
										26150.0, List.of())),
						List.of(new SkillCard.BandStat("untrained", 0, 0.6,
										0.15, 0.5, "crude", false, 1.25, 0.0),
								new SkillCard.BandStat("legendary", 95, 1.6,
										0.0, 0.5, "masterwork", true, 0.5, 0.1)),
						List.of(0, 1, 20, 40, 60, 80, 95)));

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		SkillsSummaryPayload.PACKET_CODEC.encode(buf,
				new SkillsSummaryPayload(List.of(card)));
		SkillCard d = SkillsSummaryPayload.PACKET_CODEC.decode(buf).skills().get(0);

		assertEquals(card.progress(), d.progress());
		assertEquals(card.details(), d.details());
		// Record equality covers every new field; assert the load-bearing
		// ones explicitly so a future diff reads clearly.
		assertEquals(8525.0, d.progress().xpTotal(), 0.001);
		assertEquals(2, d.details().roadmap().size());
		assertEquals(675.0, d.details().roadmap().get(0).xpTotal(), 0.001);
		assertEquals(1.6, d.details().bands().get(1).outputMult(), 0.001);
		assertEquals("masterwork", d.details().bands().get(1).qualityTier());
		assertEquals(List.of(0, 1, 20, 40, 60, 80, 95),
				d.details().bandThresholds());
	}

	@Test
	void packetCodecRoundTripsEmptyLists() {
		// Athletics/defence: no outcome rule → empty bands must still decode.
		SkillCard card = new SkillCard("lifepath:athletics",
				new SkillCard.Display(Component.literal("Athletics"),
						Component.empty(), "novice", "B", Component.empty(), ""),
				new SkillCard.Progress(1, 5.0, 45.0, 45.0, 0, 0L),
				new SkillCard.Details(10, "", List.of(), List.of(), List.of(),
						List.of(), List.of(0, 1, 20, 40, 60, 80, 95)));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		SkillsSummaryPayload.PACKET_CODEC.encode(buf,
				new SkillsSummaryPayload(List.of(card)));
		assertEquals(card, SkillsSummaryPayload.PACKET_CODEC.decode(buf)
				.skills().get(0));
	}
}
