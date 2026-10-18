package com.dwurdy.lifepath.compat.immersivemelodies;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.platform.Platform;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Emit helper for the Immersive Melodies instrument seam. Kept free of
 * immersive_melodies types — the mixin (compat config, require=0) passes only
 * vanilla signatures, so this class loads in packs without the mod.
 *
 * <p>One emit per melody start (the funnel {@code play} overload every play /
 * resume / track-refresh path lands in), not per note — xp_source data rates
 * the value, per-note spam is not a vector here.
 */
public final class ImmersiveMelodiesMusic {
	private ImmersiveMelodiesMusic() {
	}

	public static void emitPlay(Entity entity, ItemStack instrument,
			@Nullable ResourceLocation melody) {
		if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)
				|| instrument.isEmpty() || isAutomation(player)) {
			return;
		}
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(instrument.getItem());
		Set<ResourceLocation> tags = instrument.getTags()
				.map(TagKey::location)
				.collect(Collectors.toUnmodifiableSet());
		ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.MUSIC,
				itemId, tags, ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(),
				melody == null ? Map.of() : Map.of("melody", melody.toString())));
	}

	private static boolean isAutomation(Player player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}
}
