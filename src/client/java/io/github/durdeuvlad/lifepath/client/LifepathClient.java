package io.github.durdeuvlad.lifepath.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.client.character.ClientCharacterState;
import io.github.durdeuvlad.lifepath.client.network.ClientLifepathNetworking;
import io.github.durdeuvlad.lifepath.network.s2c.CharacterSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import io.github.durdeuvlad.lifepath.client.ability.ClientAbilityState;
import io.github.durdeuvlad.lifepath.client.ability.ClientHighlights;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class LifepathClient implements ClientModInitializer {
	/** Bound keys — read by screens for visible-hint footers (M6-4). */
	public static KeyMapping abilityKey;
	public static KeyMapping characterKey;

	@Override
	public void onInitializeClient() {
		LifepathMod.LOGGER.info("Lifepath client initializing (version {})", LifepathMod.modVersion());

		ClientLifepathNetworking.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.HighlightEntitiesPayload.ID,
				(payload, context) -> context.client().execute(() ->
						ClientHighlights.add(payload.entityIds(), payload.durationTicks())));
		ClientLifepathNetworking.onS2C(CharacterSyncPayload.ID, (payload, context) ->
				context.client().execute(() -> ClientCharacterState.apply(payload)));
		// M6-1: server-resolved identity strings for the character screen.
		ClientLifepathNetworking.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.IdentitySummaryPayload.ID,
				(payload, context) -> context.client().execute(() ->
						ClientCharacterState.applyIdentity(payload)));
		// M6-2: per-skill display cards for the skills screens.
		ClientLifepathNetworking.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.SkillsSummaryPayload.ID,
				(payload, context) -> context.client().execute(() ->
						ClientCharacterState.applySkills(payload)));
		// M4-4: cooldown deltas keep the client read-model fresh between
		// full snapshots (advisory — the server re-validates every eval).
		ClientLifepathNetworking.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.CooldownUpdatePayload.ID,
				(payload, context) -> context.client().execute(() ->
						ClientCharacterState.applyCooldown(
								payload.abilityId(), payload.expiryEpochMs())));
		// M4-5: resource deltas keep HUD meters fresh between snapshots.
		ClientLifepathNetworking.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.ResourceUpdatePayload.ID,
				(payload, context) -> context.client().execute(() ->
						ClientCharacterState.applyResource(payload.resourceId(),
								payload.current(), payload.min(), payload.max(),
								payload.bandIndex())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientCharacterState.clear();
			ClientAbilityState.clear();
			ClientHighlights.clear();
			io.github.durdeuvlad.lifepath.client.feedback.ClientFeedback.clear();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientCharacterState.clear());

		// M6-4: feedback events — localized chat/actionbar; ready-watcher
		// derives "ability ready" from the synced cooldown map.
		ClientLifepathNetworking.onS2C(
				io.github.durdeuvlad.lifepath.network.s2c.FeedbackPayload.ID,
				(payload, context) -> context.client().execute(() ->
						io.github.durdeuvlad.lifepath.client.feedback
								.ClientFeedback.handle(payload)));

		// M6-3: HUD overlay — relevance-gated resources/cooldowns/states,
		// read-only on synced state (config: client.toml hud_*).
		net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT
				.register(io.github.durdeuvlad.lifepath.client.hud.LifepathHud::render);

		// M12-1: icon texture existence is memoized — drop the memo on
		// resource reload (F3+T / pack change) so a texture that just arrived
		// is picked up and warn-once can re-fire for genuinely missing ones.
		net.fabricmc.fabric.api.resource.ResourceManagerHelper
				.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
				.registerReloadListener(new net.fabricmc.fabric.api.resource
						.SimpleSynchronousResourceReloadListener() {
					@Override
					public net.minecraft.resources.ResourceLocation getFabricId() {
						return LifepathMod.id("icon_cache");
					}

					@Override
					public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
						io.github.durdeuvlad.lifepath.client.icon.ClientIcons.invalidate();
					}
				});

		// M4-1: rebindable ability key (default G) — sends a C2S activation
		// request for the client-selected ability; the server validates all.
		abilityKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.lifepath.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G,
				"key.categories.lifepath"));
		// M6-1: character screen key (default C) — opens the read-only
		// identity hub; the screen renders synced state, never mutates it.
		characterKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.lifepath.character", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,
				"key.categories.lifepath"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
		});
	}
}
