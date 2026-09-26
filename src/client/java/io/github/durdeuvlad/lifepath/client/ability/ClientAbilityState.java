package io.github.durdeuvlad.lifepath.client.ability;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side ability activation state (M4-1). Holds the currently selected
 * ability id the keybind should fire — chosen by the M6 selection UI; until
 * then it stays null and keypresses are a logged no-op. The client NEVER
 * decides anything authoritative: the packet is a request the server fully
 * re-validates.
 */
public final class ClientAbilityState {
	private static Identifier selected;

	private ClientAbilityState() {}

	@Nullable
	public static Identifier selected() {
		return selected;
	}

	/** The M6 UI will set this; exposed for that future seam and dev testing. */
	public static void select(@Nullable Identifier abilityId) {
		selected = abilityId;
	}

	/**
	 * Sends an activation request for {@link #selected} if set. Returns true
	 * when a request was actually sent.
	 */
	public static boolean requestActivation() {
		if (selected == null) {
			LifepathMod.LOGGER.debug("ability key pressed with nothing selected");
			return false;
		}
		// send() throws IllegalStateException when the client isn't in a game —
		// a keypress during disconnect/unload must degrade to a no-op.
		if (net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler() == null) {
			return false;
		}
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new io.github.durdeuvlad.lifepath.network.c2s
						.ActivateAbilityPayload(selected));
		return true;
	}

	/** Clears the selection — called on disconnect so ids don't cross servers. */
	public static void clear() {
		selected = null;
	}

	/**
	 * Milliseconds remaining on {@code abilityId}'s cooldown per the synced
	 * read-model (M4-4) — advisory for HUD display only; the server owns the
	 * truth. Returns 0 when no snapshot or the cooldown is inactive.
	 */
	public static long remainingMillis(Identifier abilityId, long nowEpochMs) {
		var snapshot = ClientCharacterState.snapshot();
		if (snapshot == null) {
			return 0L;
		}
		Long expiry = snapshot.cooldowns().get(abilityId);
		return expiry == null ? 0L : Math.max(0L, expiry - nowEpochMs);
	}
}
