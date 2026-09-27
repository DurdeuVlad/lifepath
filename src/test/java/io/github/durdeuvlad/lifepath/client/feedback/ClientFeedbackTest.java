package io.github.durdeuvlad.lifepath.client.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * M6-4: {@code ability_denied} arg routing — {@code secondsLeft} must land on
 * the reason's own {@code %ss} placeholder ("on cooldown (42s)"), not the
 * outer {@code "%s — %s"} where it was dropped and the unbound placeholder
 * rendered a literal "null". Asserted structurally — the client Language
 * table isn't loaded in unit tests.
 */
class ClientFeedbackTest {

	@Test
	void cooldownDenialFeedsSecondsToTheReason() {
		Component msg = ClientFeedback.messageFor(new FeedbackPayload(
				"ability_denied", List.of("Frost Nova", "cooldown", "42")));
		TranslatableContents outer =
				(TranslatableContents) msg.getContents();
		assertEquals("feedback.lifepath.ability_denied", outer.getKey());
		assertEquals(2, outer.getArgs().length);
		assertEquals("Frost Nova", outer.getArgs()[0]);
		TranslatableContents reason =
				(TranslatableContents) ((Component) outer.getArgs()[1])
						.getContents();
		assertEquals("feedback.lifepath.reason.cooldown", reason.getKey());
		// The placeholder resolves to the sent seconds — "42", not "null".
		assertEquals("42", reason.getArgument(0).getString());
	}

	@Test
	void nonCooldownReasonStillTranslates() {
		Component msg = ClientFeedback.messageFor(new FeedbackPayload(
				"ability_denied", List.of("Frost Nova", "unavailable", "0")));
		TranslatableContents outer =
				(TranslatableContents) msg.getContents();
		TranslatableContents reason =
				(TranslatableContents) ((Component) outer.getArgs()[1])
						.getContents();
		assertEquals("feedback.lifepath.reason.unavailable", reason.getKey());
	}
}
