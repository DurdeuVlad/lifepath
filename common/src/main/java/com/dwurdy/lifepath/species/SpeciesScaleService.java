package com.dwurdy.lifepath.species;

import com.dwurdy.lifepath.character.CharacterManager;
import com.dwurdy.lifepath.character.PlayerCharacterData;
import com.dwurdy.lifepath.compat.pehkui.PehkuiScale;
import com.dwurdy.lifepath.content.SpeciesDefinition;
import com.dwurdy.lifepath.platform.Platform;
import com.dwurdy.lifepath.registry.LifepathContent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies a species' optional {@code scale} through the Pehkui bridge
 * (compat A5). Species without a {@code scale} field — and packs without
 * Pehkui — resolve to base scale 1.0, which also repairs a stale scale if
 * the player swapped off a scaled species.
 *
 * <p>Reapply points: species set (selection UI + admin command), join, and
 * respawn — Pehkui resets scales on respawn unless per-entity persistence
 * is set, which {@link PehkuiScale} does, but reapplying is cheap and also
 * covers packs where another system owns respawn state.
 */
public final class SpeciesScaleService {
	private SpeciesScaleService() {
	}

	/** Registers lifecycle hooks. Called once during common mod init. */
	public static void init() {
		var platform = Platform.get();
		platform.onPlayerJoin(SpeciesScaleService::apply);
		platform.onPlayerRespawn((oldPlayer, newPlayer, alive) -> apply(newPlayer));
	}

	/** Resolves the player's species scale and pushes it to Pehkui. */
	public static void apply(ServerPlayer player) {
		PlayerCharacterData data = CharacterManager.getCharacter(player);
		ResourceLocation speciesId = data == null ? null : data.speciesId();
		SpeciesDefinition def = speciesId == null
				? null
				: LifepathContent.species().get(speciesId);
		double scale = def == null ? 1.0 : def.scale().orElse(1.0);
		PehkuiScale.setBaseScale(player, (float) scale);
	}
}
