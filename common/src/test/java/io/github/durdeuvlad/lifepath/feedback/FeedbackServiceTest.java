package io.github.durdeuvlad.lifepath.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.durdeuvlad.lifepath.character.PlayerCharacterData;
import io.github.durdeuvlad.lifepath.feedback.FeedbackService.SyncDiff;
import io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * M6-4 feedback diff contract: the sync-funnel diff decides which identity/
 * condition changes become messages; first sync is a silent baseline.
 */
class FeedbackServiceTest {
	private static final ResourceLocation ICE = ResourceLocation.parse("lifepath:iceborn");
	private static final ResourceLocation HUMAN = ResourceLocation.parse("lifepath:human");
	private static final ResourceLocation MINER = ResourceLocation.parse("lifepath:miner");
	private static final ResourceLocation CHILL = ResourceLocation.parse("lifepath:chilled");

	private static FeedbackService.Prev prev(PlayerCharacterData d) {
		// Prev is private — construct through the public diff seam's shape.
		return new FeedbackService.Prev(d.speciesId(), d.specializationId(),
				new java.util.HashSet<>(d.conditions()));
	}

	@Test
	void firstSyncIsSilentBaseline() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(ICE);
		data.setSpecializationId(MINER);
		data.addId(PlayerCharacterData.ListKind.CONDITIONS, CHILL);
		SyncDiff d = FeedbackService.diff(null, data);
		assertTrue(d.isEmpty(), "join sync must not spam gained/assigned");
	}

	@Test
	void assignmentAndConditionChangesDiff() {
		PlayerCharacterData before = PlayerCharacterData.createDefault();
		before.setSpeciesId(HUMAN);
		before.addId(PlayerCharacterData.ListKind.CONDITIONS, CHILL);
		var p = prev(before);

		PlayerCharacterData after = PlayerCharacterData.createDefault();
		after.setSpeciesId(ICE);            // changed
		after.setSpecializationId(MINER);   // newly assigned
		// CHILL lost; nothing gained
		SyncDiff d = FeedbackService.diff(p, after);
		assertEquals(ICE, d.speciesAssigned());
		assertEquals(MINER, d.specAssigned());
		assertTrue(d.conditionsGained().isEmpty());
		assertEquals(List.of(CHILL), d.conditionsLost());
	}

	@Test
	void noChangeDiffsEmpty() {
		PlayerCharacterData data = PlayerCharacterData.createDefault();
		data.setSpeciesId(ICE);
		data.addId(PlayerCharacterData.ListKind.CONDITIONS, CHILL);
		assertTrue(FeedbackService.diff(prev(data), data).isEmpty());
	}

	@Test
	void sameIdReassignmentIsNotAChange() {
		PlayerCharacterData a = PlayerCharacterData.createDefault();
		a.setSpeciesId(HUMAN);
		PlayerCharacterData b = PlayerCharacterData.createDefault();
		b.setSpeciesId(HUMAN);
		assertNull(FeedbackService.diff(prev(a), b).speciesAssigned());
	}

	@Test
	void payloadCodecRoundTrips() {
		// Payload contract: kind + string args survive the codec untouched.
		FeedbackPayload p = new FeedbackPayload("level_up",
				List.of("Mining", "30"));
		assertEquals("level_up", p.kind());
		assertEquals(List.of("Mining", "30"), p.args());
	}
}
