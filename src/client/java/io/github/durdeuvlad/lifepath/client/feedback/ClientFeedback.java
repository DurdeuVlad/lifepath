package io.github.durdeuvlad.lifepath.client.feedback;

import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * M6-4 client feedback sink. Server-triggered {@link FeedbackPayload}s are
 * rendered localized: transient ability events go to the actionbar,
 * everything else to chat. The "ability ready" notice is derived client-side
 * from the synced cooldown map — when a tracked expiry passes, the player is
 * told once. No state is mutated here; this is a renderer for server truth.
 */
@Environment(EnvType.CLIENT)
public final class ClientFeedback {
	/** Cooldowns we have seen, for ready-transition detection. */
	private static final Map<Identifier, Long> SEEN_COOLDOWNS = new HashMap<>();

	private ClientFeedback() {
	}

	/** Handles one server feedback packet on the client thread. */
	public static void handle(FeedbackPayload payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		Text message = messageFor(payload);
		boolean actionbar = switch (payload.kind()) {
			case "ability_denied", "ability_ready" -> true;
			default -> false;
		};
		if (actionbar) {
			client.player.sendMessage(message, true);
		} else {
			client.player.sendMessage(message, false);
		}
	}

	/** Builds the localized message for a payload kind + args. */
	static Text messageFor(FeedbackPayload p) {
		String key = "feedback.lifepath." + p.kind();
		Object[] args = p.args().toArray();
		// Kinds whose last arg is a lang key get it translated client-side.
		return switch (p.kind()) {
			case "milestone" -> Text.translatable(key, arg(p, 0), arg(p, 1),
					arg(p, 2).isEmpty() ? Text.empty()
							: Text.translatable(arg(p, 2)));
			case "ability_denied" -> Text.translatable(key, arg(p, 0),
					Text.translatable("feedback.lifepath.reason." + arg(p, 1)),
					arg(p, 2));
			default -> Text.translatable(key, args);
		};
	}

	private static String arg(FeedbackPayload p, int i) {
		return i < p.args().size() ? p.args().get(i) : "";
	}

	/**
	 * Per-tick cooldown watcher: an expiry that passes since last tick yields
	 * one "X ready" actionbar notice naming the ability key.
	 */
	public static void tickReadyWatcher(MinecraftClient client,
			KeyBinding abilityKey) {
		var snapshot = ClientCharacterState.snapshot();
		long now = System.currentTimeMillis();
		Map<Identifier, Long> current = snapshot == null
				? Map.of() : snapshot.cooldowns();
		// Fire transitions: armed cooldowns whose expiry has now passed.
		Iterator<Map.Entry<Identifier, Long>> it =
				SEEN_COOLDOWNS.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			// Disarm entries cleared server-side (reset/cancel) — no notice.
			if (!current.containsKey(e.getKey())) {
				it.remove();
				continue;
			}
			if (e.getValue() <= now) {
				it.remove();
				if (client.player != null) {
					var ability = ClientCharacterState.identity()
							.abilities().get(e.getKey().toString());
					String name = ability != null
							? ability.name() : e.getKey().getPath();
					client.player.sendMessage(Text.translatable(
							"feedback.lifepath.ability_ready", name,
							abilityKey.getBoundKeyLocalizedText()), true);
				}
			}
		}
		// Arm active cooldowns (re-triggering refreshes the expiry). Entries
		// already expired stay disarmed — they fire exactly once.
		for (var e : current.entrySet()) {
			if (e.getValue() > now) {
				SEEN_COOLDOWNS.put(e.getKey(), e.getValue());
			}
		}
	}

	/** Drops watcher state on disconnect/clear. */
	public static void clear() {
		SEEN_COOLDOWNS.clear();
	}
}
