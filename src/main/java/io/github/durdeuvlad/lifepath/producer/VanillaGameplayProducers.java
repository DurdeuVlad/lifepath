package io.github.durdeuvlad.lifepath.producer;

import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.ActivityTypes;
import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Vanilla gameplay → normalized events (M2-4). This is the boundary where raw
 * Minecraft hooks become {@link ActivityEvent}s — the ONLY layer allowed to
 * know about {@code CropBlock}, block states, and placement mechanics.
 * Producers emit; they never decide XP amounts (xp_source data does that).
 *
 * <p>Hooks:
 * <ul>
 *   <li>{@code PlayerBlockBreakEvents.AFTER} — fires for PLAYER breaks only,
 *       so TNT/piston/automation breaks never produce mining events.
 *       Player-placed positions are suppressed via {@link PlacedBlockTracker}.</li>
 *   <li>Crop harvest: same event; emits farming harvest only when the crop
 *       block {@link CropBlock#isMature is mature} — immature harvests emit
 *       nothing.</li>
 *   <li>Placement: {@code mixin.BlockItemMixin} calls
 *       {@link #onBlockPlaced} — records the position and emits a farming
 *       planting event for crop blocks.</li>
 * </ul>
 */
public final class VanillaGameplayProducers {
	private VanillaGameplayProducers() {
	}

	private static final Identifier TAG_PLANTED = LifepathMod.id("planted");
	private static final Identifier TAG_HARVESTED = LifepathMod.id("harvested");
	private static final Identifier TAG_MATURE = LifepathMod.id("mature");
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		PlayerBlockBreakEvents.AFTER.register(VanillaGameplayProducers::onBlockBroken);
	}

	private static void onBlockBroken(net.minecraft.world.World world,
			net.minecraft.entity.player.PlayerEntity player, BlockPos pos,
			BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || world.isClient()) {
			return;
		}
		boolean wasPlayerPlaced = PlacedBlockTracker.consume(world, pos);
		Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
		Set<Identifier> blockTags = state.streamTags()
				.map(TagKey::id).collect(Collectors.toUnmodifiableSet());

		// Mining: only natural/unknown-origin blocks yield events — a block the
		// player placed earlier this session is the re-mine exploit pattern.
		if (!wasPlayerPlaced) {
			ActivityDispatcher.publish(new ActivityEvent(serverPlayer, ActivityTypes.MINING,
					blockId, blockTags, ActivityEvent.Cause.PLAYER, System.currentTimeMillis(),
					Map.of()));
		}

		// Farming harvest: crops only, mature only — immature harvests emit
		// nothing (spec: harvesting an unripe crop awards exactly 0 XP).
		if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
			java.util.Set<Identifier> tags = new java.util.HashSet<>(blockTags);
			tags.add(TAG_HARVESTED);
			tags.add(TAG_MATURE);
			ActivityDispatcher.publish(new ActivityEvent(serverPlayer, ActivityTypes.FARMING,
					blockId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
					System.currentTimeMillis(), Map.of("mature", "true")));
		}
	}

	/** Called by {@code mixin.BlockItemMixin} after a successful block placement. */
	public static void onBlockPlaced(ServerPlayerEntity player, ServerWorld world,
			BlockPos pos, BlockState placedState, ItemPlacementContext context, ActionResult result) {
		PlacedBlockTracker.record(world, pos);
		Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(placedState.getBlock());
		if (placedState.getBlock() instanceof CropBlock) {
			Set<Identifier> tags = placedState.streamTags()
					.map(TagKey::id).collect(Collectors.toCollection(java.util.HashSet::new));
			tags.add(TAG_PLANTED);
			Map<String, String> attrs = new LinkedHashMap<>();
			attrs.put("mature", "false");
			ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.FARMING,
					blockId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
					System.currentTimeMillis(), attrs));
		}
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
