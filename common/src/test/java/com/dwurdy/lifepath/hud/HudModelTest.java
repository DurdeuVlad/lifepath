package com.dwurdy.lifepath.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.network.s2c.IdentitySummaryPayload;
import com.dwurdy.lifepath.network.s2c.IdentitySummaryPayload
		.ResourceDisplay;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Component;

/**
 * M6-3 relevance-gating contract: idle resources and expired cooldowns produce
 * no HUD rows; deviations produce labeled rows. Pure read-model tests — the
 * renderer only draws what {@link HudModel} returns.
 */
class HudModelTest {
	private static final ResourceLocation TEMP = ResourceLocation.parse("lifepath:temperature");
	private static final ResourceLocation ABIL = ResourceLocation.parse("lifepath:frost_nova");

	private static IdentitySummaryPayload identity(List<ResourceDisplay> res,
			Map<String, IdentitySummaryPayload.AbilityEntry> abilities) {
		return new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore("lifepath:iceborn",
						Component.literal("Iceborn"), Component.empty(), "", "",
						Component.empty(), "", List.of()),
				List.of(),
				Map.of("conditions",
						List.of(new IdentitySummaryPayload.Entry(
								"lifepath:chilled", Component.literal("Chilled"), ""))),
				abilities, res, IdentitySummaryPayload.MorphView.EMPTY);
	}

	private static ResourceDisplay tempDisplay(double def, int restBand,
			List<String> bandNames) {
		return new ResourceDisplay("lifepath:temperature",
				Component.literal("Temperature"), def,
				restBand, bandNames.stream().<Component>map(Component::literal).toList(), "lifepath:textures/gui/resource/temp.png");
	}

	@Test
	void emptyWhenNoSnapshot() {
		assertTrue(HudModel.compute(identity(List.of(), Map.of()), null,
				Map.of(), 0).isEmpty());
	}

	@Test
	void resourceAtRestIsHidden() {
		PlayerCharacterData data = new PlayerCharacterData();
		data.setResource(TEMP, new PlayerCharacterData.ResourceState(50, 0, 100));
		// rest band index 1 with default 50 in band [26,74]
		IdentitySummaryPayload id = identity(List.of(
				tempDisplay(50, 1, List.of("Cold", "Temperate", "Hot"))),
				Map.of());
		HudModel.View v = HudModel.compute(id, data, Map.of(TEMP, 1), 0);
		assertTrue(v.resources().isEmpty());
		assertTrue(v.cooldowns().isEmpty());
		// conditions still surface as state rows (name + icon)
		assertEquals(1, v.states().size());
		assertEquals("Chilled", v.states().get(0).name().getString());
	}

	@Test
	void resourceDeviationShowsRowWithBandName() {
		PlayerCharacterData data = new PlayerCharacterData();
		data.setResource(TEMP, new PlayerCharacterData.ResourceState(80, 0, 100));
		IdentitySummaryPayload id = identity(List.of(
				tempDisplay(50, 1, List.of("Cold", "Temperate", "Hot"))),
				Map.of());
		HudModel.View v = HudModel.compute(id, data, Map.of(TEMP, 2), 0);
		assertEquals(1, v.resources().size());
		HudModel.ResourceRow row = v.resources().get(0);
		assertEquals("Temperature", row.label().getString());
		assertEquals("Hot", row.bandName().getString());
		assertEquals(0.8, row.fraction(), 0.001);
		assertEquals("lifepath:textures/gui/resource/temp.png", row.icon());
	}

	@Test
	void offRestBandAtDefaultValueStillShows() {
		// value at default but pushed into a non-rest band = meaningful
		PlayerCharacterData data = new PlayerCharacterData();
		data.setResource(TEMP, new PlayerCharacterData.ResourceState(50, 0, 100));
		IdentitySummaryPayload id = identity(List.of(
				tempDisplay(50, -1, List.of("Cold", "Hot"))), Map.of());
		HudModel.View v = HudModel.compute(id, data, Map.of(TEMP, 0), 0);
		assertEquals(1, v.resources().size());
		assertEquals("Cold", v.resources().get(0).bandName().getString());
	}

	@Test
	void missingResourceStateSkippedGracefully() {
		PlayerCharacterData data = new PlayerCharacterData(); // no resources
		IdentitySummaryPayload id = identity(List.of(
				tempDisplay(50, 0, List.of("Rest"))), Map.of());
		assertTrue(HudModel.compute(id, data, Map.of(), 0)
				.resources().isEmpty());
	}

	@Test
	void malformedResourceIdSkipped() {
		PlayerCharacterData data = new PlayerCharacterData();
		IdentitySummaryPayload id = identity(List.of(
				new ResourceDisplay("not a valid id!!", Component.literal("Bad"), 0, 0,
						List.of(),
						"")),
				Map.of());
		assertTrue(HudModel.compute(id, data, Map.of(), 0)
				.resources().isEmpty());
	}

	@Test
	void cooldownsShowRemainingSortedAscending() {
		PlayerCharacterData data = new PlayerCharacterData();
		data.setCooldown(ABIL, 5_000L);
		data.setCooldown(ResourceLocation.parse("lifepath:verdant_bloom"), 9_000L);
		IdentitySummaryPayload id = identity(List.of(), Map.of(
				"lifepath:frost_nova", new IdentitySummaryPayload.AbilityEntry(
						"lifepath:frost_nova", Component.literal("Frost Nova"),
						"lifepath:textures/gui/ability/frost.png", true,
						Component.empty())));
		HudModel.View v = HudModel.compute(id, data, Map.of(), 1_000L);
		assertEquals(2, v.cooldowns().size());
		assertEquals("Frost Nova", v.cooldowns().get(0).label().getString()); // 4s left first
		assertEquals(4.0, v.cooldowns().get(0).secondsLeft(), 0.001);
		// M12-3: the row carries the ability's icon ref for the badge.
		assertEquals("lifepath:textures/gui/ability/frost.png",
				v.cooldowns().get(0).icon());
		// unnamed ability falls back to its path, icon empty
		assertEquals("verdant_bloom", v.cooldowns().get(1).label().getString());
		assertEquals("", v.cooldowns().get(1).icon());
	}

	@Test
	void expiredCooldownHidden() {
		PlayerCharacterData data = new PlayerCharacterData();
		data.setCooldown(ABIL, 1_000L);
		IdentitySummaryPayload id = identity(List.of(), Map.of());
		HudModel.View v = HudModel.compute(id, data, Map.of(), 5_000L);
		assertTrue(v.cooldowns().isEmpty());
	}

	@Test
	void emptyIdentityYieldsOnlyRawStates() {
		PlayerCharacterData data = new PlayerCharacterData();
		data.setCooldown(ABIL, 5_000L);
		IdentitySummaryPayload empty = IdentitySummaryPayload.empty();
		HudModel.View v = HudModel.compute(empty, data, Map.of(), 0);
		assertEquals(1, v.cooldowns().size());
		assertEquals("frost_nova", v.cooldowns().get(0).label().getString()); // id path
		assertTrue(v.resources().isEmpty());
		assertTrue(v.states().isEmpty());
	}
}
