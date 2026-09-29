package io.github.durdeuvlad.lifepath.client.selection;

import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.screen.SelectionScreen;
import io.github.durdeuvlad.lifepath.config.LifepathConfig;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload;
import io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.Entry;
import io.github.durdeuvlad.lifepath.platform.ClientOnly;
import net.minecraft.client.Minecraft;

/**
 * M14 client read-model for the selection catalog. Same contract as
 * {@link ClientCharacterState}: a mirror of server-resolved truth the
 * screens render — mutating it locally is meaningless, the server decides.
 *
 * <p>Also owns the once-per-session auto-open: when the join-time catalog
 * arrives and the player still has no species, the picker pops itself
 * (unless {@code client.toml onboarding_auto_open=false}). Dismissed or
 * denied-once it stays shut until the next session or a manual open via the
 * character screen's Choose button.
 */
@ClientOnly
public final class ClientSelectionState {
	private static volatile SelectionCatalogPayload catalog = SelectionCatalogPayload.empty();
	private static boolean autoOpenArmed = true;

	private ClientSelectionState() {
	}

	public static void apply(SelectionCatalogPayload payload) {
		catalog = payload;
	}

	public static SelectionCatalogPayload catalog() {
		return catalog;
	}

	/** Drops the snapshot on join/disconnect and re-arms the once-per-session auto-open. */
	public static void clear() {
		catalog = SelectionCatalogPayload.empty();
		autoOpenArmed = true;
	}

	/**
	 * Called from the catalog handler: pops the picker on join while the
	 * species pick is outstanding. One-shot per session — any skip disarms
	 * (the manual entry point always remains).
	 */
	public static void maybeOpenOnboarding(Minecraft client) {
		if (!autoOpenArmed) {
			return;
		}
		autoOpenArmed = false;
		if (client.player == null || client.screen != null
				|| client.player.isDeadOrDying()
				|| !LifepathConfig.isLoaded(LifepathConfig.CLIENT)
				|| !LifepathConfig.getBoolean(LifepathConfig.CLIENT, "onboarding_auto_open")) {
			return;
		}
		if (!ClientCharacterState.identity().identity().speciesName().isEmpty()) {
			return;
		}
		boolean anyAvailable = catalog.species().stream()
				.anyMatch(e -> e.availability() == Entry.AVAILABLE);
		if (anyAvailable) {
			client.setScreen(new SelectionScreen(SelectionScreen.Step.SPECIES));
		}
	}
}
