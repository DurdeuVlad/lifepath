package io.github.durdeuvlad.lifepath.client.ability;

import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side ability activation state (M4-1). Holds the currently selected
 * ability id the keybind should fire — set by clicking an ability row in the
 * character screen. With no selection the key sends the AUTO sentinel so the
 * server picks the first owned ACTIVE ability. The client NEVER decides
 * anything authoritative: the packet is a request the server fully
 * re-validates.
 */
public final class ClientAbilityState {
	private static ResourceLocation selected;

	private ClientAbilityState() {}

	@Nullable
	public static ResourceLocation selected() {
		return selected;
	}

	/** Called by the character screen ability list; cleared on disconnect. */
	public static void select(@Nullable ResourceLocation abilityId) {
		selected = abilityId;
	}

	/**
	 * Sends an activation request — the {@link #selected} ability, or the
	 * AUTO sentinel so the server resolves the first owned ACTIVE ability
	 * when nothing was picked. Returns true when a request was sent.
	 */
	public static boolean requestActivation() {
		// send() throws IllegalStateException when the client isn't in a game —
		// a keypress during disconnect/unload must degrade to a no-op.
		if (net.minecraft.client.Minecraft.getInstance().getConnection() == null) {
			return false;
		}
		ResourceLocation abilityId = selected != null
				? selected
				: io.github.durdeuvlad.lifepath.network.c2s
						.ActivateAbilityPayload.AUTO;
		io.github.durdeuvlad.lifepath.client.platform.ClientPlatform.get()
				.sendToServer(new io.github.durdeuvlad.lifepath.network.c2s
						.ActivateAbilityPayload(abilityId));
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
	public static long remainingMillis(ResourceLocation abilityId, long nowEpochMs) {
		var snapshot = ClientCharacterState.snapshot();
		if (snapshot == null) {
			return 0L;
		}
		Long expiry = snapshot.cooldowns().get(abilityId);
		return expiry == null ? 0L : Math.max(0L, expiry - nowEpochMs);
	}
}
