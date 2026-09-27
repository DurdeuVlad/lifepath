package io.github.durdeuvlad.lifepath.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload;
import io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload
		.ResourceDisplay;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

/**
 * M6-3 relevance-gating contract: idle resources and expired cooldowns produce
 * no HUD rows; deviations produce labeled rows. Pure read-model tests — the
 * renderer only draws what {@link HudModel} returns.
 */
class HudModelTest {
	private static final Identifier TEMP = Identifier.of("lifepath:temperature");
	private static final Identifier ABIL = Identifier.of("lifepath:frost_nova");

	private static IdentitySummaryPayload identity(List<ResourceDisplay> res,
			Map<String, IdentitySummaryPayload.AbilityEntry> abilities) {
		return new IdentitySummaryPayload(
				new IdentitySummaryPayload.IdentityCore("lifepath:iceborn",
						"Iceborn", "", "", "", "", ""),
				List.of(),
				Map.of("conditions",
						List.of(new IdentitySummaryPayload.Entry(
								"lifepath:chilled", "Chilled", ""))),
				abilities, res);
	}

	private static ResourceDisplay tempDisplay(double def, int restBand,
			List<String> bandNames) {
		return new ResourceDisplay("lifepath:temperature", "Temperature", def,
				restBand, bandNames, "lifepath:textures/gui/resource/temp.png");
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
		assertEquals("Chilled", v.states().get(0).name());
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
		assertEquals("Temperature", row.label());
		assertEquals("Hot", row.bandName());
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
		assertEquals("Cold", v.resources().get(0).bandName());
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
				new ResourceDisplay("not a valid id!!", "Bad", 0, 0, List.of(),
						"")),
				Map.of());
		assertTrue(HudModel.compute(id, data, Map.of(), 0)
				.resources().isEmpty());
	}

	@Test
	void cooldownsShowRemainingSortedAscending() {
		PlayerCharacterData data = new PlayerCharacterData();
		data.setCooldown(ABIL, 5_000L);
		data.setCooldown(Identifier.of("lifepath:verdant_bloom"), 9_000L);
		IdentitySummaryPayload id = identity(List.of(), Map.of(
				"lifepath:frost_nova", new IdentitySummaryPayload.AbilityEntry(
						"lifepath:frost_nova", "Frost Nova",
						"lifepath:textures/gui/ability/frost.png", true)));
		HudModel.View v = HudModel.compute(id, data, Map.of(), 1_000L);
		assertEquals(2, v.cooldowns().size());
		assertEquals("Frost Nova", v.cooldowns().get(0).label()); // 4s left first
		assertEquals(4.0, v.cooldowns().get(0).secondsLeft(), 0.001);
		// M12-3: the row carries the ability's icon ref for the badge.
		assertEquals("lifepath:textures/gui/ability/frost.png",
				v.cooldowns().get(0).icon());
		// unnamed ability falls back to its path, icon empty
		assertEquals("verdant_bloom", v.cooldowns().get(1).label());
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
		assertEquals("frost_nova", v.cooldowns().get(0).label()); // id path
		assertTrue(v.resources().isEmpty());
		assertTrue(v.states().isEmpty());
	}
}
