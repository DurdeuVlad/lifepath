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
 * (unless {@code client.toml onboarding_auto_open=false}). The catalog lands
 * while the terrain-download screen is still up, so the open is deferred to
 * the first client tick with a free screen ({@link #tickAutoOpen}) rather
 * than attempted — and lost — inside the packet callback. Dismissed or
 * denied-once it stays shut until the next session or a manual open via the
 * character screen's Choose button.
 */
@ClientOnly
public final class ClientSelectionState {
	private static volatile SelectionCatalogPayload catalog = SelectionCatalogPayload.empty();
	private static boolean autoOpenArmed = true;
	/** Ticks left to find a free screen for the pending auto-open; negative = none. */
	private static int autoOpenTicks = -1;
	/** ~10s at 20 tps — long enough for slow chunk streams, short enough that
	 * a screen that stays busy means the player is doing something else. */
	private static final int AUTO_OPEN_WINDOW_TICKS = 200;

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
		autoOpenTicks = -1;
	}

	/**
	 * Called from the catalog handler: arms the picker while the species
	 * pick is outstanding. The open itself happens in {@link #tickAutoOpen}
	 * once the join screens clear. One-shot per session — any skip disarms
	 * (the manual entry point always remains).
	 */
	public static void maybeOpenOnboarding(Minecraft client) {
		if (!autoOpenArmed) {
			return;
		}
		autoOpenArmed = false;
		if (!LifepathConfig.isLoaded(LifepathConfig.CLIENT)
				|| !LifepathConfig.getBoolean(LifepathConfig.CLIENT, "onboarding_auto_open")) {
			return;
		}
		// M-2: a morph-capable species pick with no form still owes a pick —
		// arm the picker the same way (the screen opens on the form step).
		var identity = ClientCharacterState.identity();
		if (!identity.identity().speciesId().isEmpty()
				&& identity.morph().formId().isEmpty()
				&& catalog.morphSpecies().contains(identity.identity().speciesId())
				&& catalog.morphForms().stream()
						.anyMatch(e -> e.availability() == Entry.AVAILABLE)) {
			autoOpenTicks = AUTO_OPEN_WINDOW_TICKS;
			return;
		}
		if (!ClientCharacterState.identity().identity().speciesName()
				.getString().isEmpty()) {
			return;
		}
		boolean anyAvailable = catalog.species().stream()
				.anyMatch(e -> e.availability() == Entry.AVAILABLE);
		if (anyAvailable) {
			autoOpenTicks = AUTO_OPEN_WINDOW_TICKS;
		}
	}

	/**
	 * Polls the deferred auto-open from the end-client-tick hook. Waits for
	 * a free screen (the catalog arrives under the terrain-download screen),
	 * gives up on disconnect or after {@link #AUTO_OPEN_WINDOW_TICKS}.
	 */
	public static void tickAutoOpen(Minecraft client) {
		if (autoOpenTicks < 0) {
			return;
		}
		if (client.player == null || --autoOpenTicks < 0) {
			autoOpenTicks = -1;
			return;
		}
		if (client.screen != null || client.player.isDeadOrDying()) {
			return;
		}
		autoOpenTicks = -1;
		client.setScreen(new SelectionScreen(SelectionScreen.Step.SPECIES));
	}
}
