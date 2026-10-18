package com.dwurdy.lifepath.mixin;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvents;
import com.dwurdy.lifepath.platform.Platform;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RiverFishing catches → {@code lifepath:fishing} activity (NeoForge-only,
 * gated by the {@code lifepath.compat.riverfishing.mixins.json} config's
 * {@code requiredMods}). Verified against
 * riverfishing-neoforge-1.0.0+1.21.1 bytecode: RiverFishing runs a fully
 * bespoke session engine — its rods never touch {@code FishingHook} or loot
 * tables, so the vanilla bobber mixin sees nothing.
 *
 * <p>The canonical take point {@code private static giveFish(ServerPlayer,
 * ResourceLocation speciesId, int, int, boolean, boolean, boolean, BlockPos,
 * FishingSession)} is called only from {@code landFish} — but its trailing
 * {@code FishingSession} parameter is a mod type unavailable to this
 * compile classpath, so the hook lands on {@code rollMorph(ServerPlayer,
 * ItemStack fish, ResourceLocation speciesId, int weight, BlockPos pond)}
 * instead: invoked exactly once, unconditionally, at the top of every
 * {@code giveFish} (bytecode offset 38), carrying the same player + species
 * plus the freshly built fish stack. One inject covers every rod/pod-fight
 * catch path.
 *
 * <p>Event shape: sourceId = species id (identical to the fish item id —
 * {@code ModItems.fishItem} keys {@code FISH_ITEMS} by it), tags = the caught
 * stack's item tags ({@code riverfishing:fishes} et al.), so xp_source
 * {@code per_subject}/{@code per_tag} data owns all weighting.
 */
@Pseudo
@Mixin(targets = "com.riverfishing.fishing.FishingManager", remap = false)
public abstract class RiverFishingManagerMixin {

	@Inject(method = "rollMorph", at = @At("HEAD"), remap = false, require = 0)
	private static void lifepath$fishGiven(ServerPlayer player, ItemStack fish,
			ResourceLocation speciesId, int weight, BlockPos pond, CallbackInfo ci) {
		if (player == null || speciesId == null || fish.isEmpty()
				|| lifepath$isAutomation(player) || lifepath$isIllegalCatch(fish)) {
			return;
		}
		Set<ResourceLocation> itemTags = fish.getTags().map(TagKey::location)
				.collect(Collectors.toCollection(HashSet::new));
		ActivityDispatcher.publish(ActivityEvents.fishing(player, speciesId,
				itemTags, Map.of("count", Integer.toString(fish.getCount()),
						"source", "riverfishing")));
	}

	/** Same contract as the vanilla producers: machine players (Create
	 * deployers, loader FakePlayers) pass {@code instanceof ServerPlayer}, so
	 * every attributed event must exclude them. Uninitialized platform (tests)
	 * conservatively means "real". */
	/** RiverFishing stamps the CatchCard into {@code custom_data}; its root
	 * {@code Legal=false} marks poached/out-of-regulation catches that the
	 * pack's KubeJS bridge suppresses XP for. Suppress only when the flag is
	 * present AND false — a card not yet attached (or stripped) stays
	 * award-eligible, matching the bridge's fail-open read. */
	@Unique
	private static boolean lifepath$isIllegalCatch(ItemStack fish) {
		try {
			var data = fish.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
			if (data == null) {
				return false;
			}
			net.minecraft.nbt.CompoundTag tag = data.copyTag();
			return tag.contains("Legal") && !tag.getBoolean("Legal");
		} catch (Throwable t) {
			return false;
		}
	}

	@Unique
	private static boolean lifepath$isAutomation(ServerPlayer player) {
		return Platform.isInitialized() && Platform.get().isAutomation(player);
	}
}
