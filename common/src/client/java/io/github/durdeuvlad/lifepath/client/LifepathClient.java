package io.github.durdeuvlad.lifepath.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.client.ability.ClientAbilityState;
import io.github.durdeuvlad.lifepath.client.ability.ClientHighlights;
import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.network.ClientLifepathNetworking;
import io.github.durdeuvlad.lifepath.client.platform.ClientPlatform;
import io.github.durdeuvlad.lifepath.network.s2c.CharacterSyncPayload;
import io.github.durdeuvlad.lifepath.platform.ClientOnly;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

@ClientOnly
public final class LifepathClient {
	private LifepathClient() {
	}

	/** Bound keys — read by screens for visible-hint footers (M6-4). */
	public static KeyMapping abilityKey;
	public static KeyMapping characterKey;

	/**
	 * Client bootstrap. Called by the loader's client entrypoint after
	 * {@code ClientPlatform.init(...)} — e.g. {@code FabricLifepathClient}.
	 */
	public static void init() {
		var clientPlatform = ClientPlatform.get();
		LifepathMod.LOGGER.info("Lifepath client initializing (version {})", LifepathMod.modVersion());

		clientPlatform.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.HighlightEntitiesPayload.ID,
				(payload, client) -> client.execute(() ->
						ClientHighlights.add(payload.entityIds(), payload.durationTicks())));
		clientPlatform.onS2C(CharacterSyncPayload.ID, (payload, client) ->
				client.execute(() -> ClientCharacterState.apply(payload)));
		// M6-1: server-resolved identity strings for the character screen.
		clientPlatform.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload.ID,
				(payload, client) -> client.execute(() ->
						ClientCharacterState.applyIdentity(payload)));
		// M6-2: per-skill display cards for the skills screens.
		clientPlatform.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload.ID,
				(payload, client) -> client.execute(() ->
						ClientCharacterState.applySkills(payload)));
		// M14: the selection catalog drives the onboarding picker — on join
		// it arrives after the identity sync, so "species still unset" is a
		// trustworthy auto-open signal.
		clientPlatform.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.SelectionCatalogPayload.ID,
				(payload, client) -> client.execute(() -> {
					io.github.durdeuvlad.lifepath.client.selection
							.ClientSelectionState.apply(payload);
					io.github.durdeuvlad.lifepath.client.selection
							.ClientSelectionState.maybeOpenOnboarding(client);
				}));
		// M4-4: cooldown deltas keep the client read-model fresh between
		// full snapshots (advisory — the server re-validates every eval).
		clientPlatform.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.CooldownUpdatePayload.ID,
				(payload, client) -> client.execute(() ->
						ClientCharacterState.applyCooldown(
								payload.abilityId(), payload.expiryEpochMs())));
		// M4-5: resource deltas keep HUD meters fresh between snapshots.
		clientPlatform.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.ResourceUpdatePayload.ID,
				(payload, client) -> client.execute(() ->
						ClientCharacterState.applyResource(payload.resourceId(),
								payload.current(), payload.min(), payload.max(),
								payload.bandIndex())));
		clientPlatform.onClientDisconnect(() -> {
			ClientCharacterState.clear();
			ClientAbilityState.clear();
			ClientHighlights.clear();
			io.github.durdeuvlad.lifepath.client.selection.ClientSelectionState.clear();
			io.github.durdeuvlad.lifepath.client.feedback.ClientFeedback.clear();
			io.github.durdeuvlad.lifepath.client.morph.MorphDisguiseClient.clear();
		});
		clientPlatform.onClientJoin(() -> {
			ClientCharacterState.clear();
			io.github.durdeuvlad.lifepath.client.selection.ClientSelectionState.clear();
		});

		// M6-4: feedback events — localized chat/actionbar; ready-watcher
		// derives "ability ready" from the synced cooldown map.
		clientPlatform.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload.ID,
				(payload, client) -> client.execute(() ->
						io.github.durdeuvlad.lifepath.client.feedback
								.ClientFeedback.handle(payload)));

		// M6-3: HUD overlay — relevance-gated resources/cooldowns/states,
		// read-only on synced state (config: client.toml hud_*).
		clientPlatform.onHudRender(io.github.durdeuvlad.lifepath.client.hud.LifepathHud::render);

		// M12-1: icon texture existence is memoized — drop the memo on
		// resource reload (F3+T / pack change) so a texture that just arrived
		// is picked up and warn-once can re-fire for genuinely missing ones.
		clientPlatform.registerClientReloadListener(LifepathMod.id("icon_cache"),
				manager -> io.github.durdeuvlad.lifepath.client.icon.ClientIcons.invalidate());

		// M4-1: rebindable ability key (default G) — sends a C2S activation
		// request for the client-selected ability; the server validates all.
		abilityKey = clientPlatform.registerKeyMapping(new KeyMapping(
				"key.lifepath.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
				"key.categories.lifepath"));
		// M6-1: character screen key (default O — vanilla claims C for
		// saveToolbarActivator, and O is the Origin-style convention players
		// already know) — opens the read-only identity hub; the screen
		// renders synced state, never mutates it.
		characterKey = clientPlatform.registerKeyMapping(new KeyMapping(
				"key.lifepath.character", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O,
				"key.categories.lifepath"));
		clientPlatform.onEndClientTick(client -> {
			while (abilityKey.consumeClick()) {
				ClientAbilityState.requestActivation();
			}
			while (characterKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(
							new io.github.durdeuvlad.lifepath.client.screen
									.CharacterScreen());
				}
			}
			ClientHighlights.tick(client);
			io.github.durdeuvlad.lifepath.client.feedback.ClientFeedback
					.tickReadyWatcher(client, abilityKey);
			io.github.durdeuvlad.lifepath.client.selection.ClientSelectionState
					.tickAutoOpen(client);
		});
	}
}
