package io.github.durdeuvlad.lifepath.producer;

import io.github.durdeuvlad.lifepath.event.ActivityDispatcher;
import io.github.durdeuvlad.lifepath.event.ActivityEvent;
import io.github.durdeuvlad.lifepath.event.ActivityTypes;
import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Vanilla gameplay → normalized events (M2-4). This is the boundary where raw
 * Minecraft hooks become {@link ActivityEvent}s — the ONLY layer allowed to
 * know about block states, tags, and placement mechanics. Producers emit;
 * they never decide XP amounts (xp_source data does that).
 *
 * <p>Farming eligibility is tag-driven, not class-driven: a block is farmable
 * if it is in {@code minecraft:crops} OR {@code lifepath:harvestable}
 * (break-harvest non-crops like cocoa/nether wart) OR
 * {@code lifepath:click_harvest} (right-click harvesters like berry bushes).
 * Maturity is property-driven: CropBlock.isMature, then a {@code berries}
 * boolean property, then an {@code age} int property at max — so datapack
 * crops work without Java changes.
 *
 * <p>Hooks:
 * <ul>
 *   <li>{@code PlayerBlockBreakEvents.AFTER} — player breaks only (TNT/
 *       piston/automation never fire it). Mining events suppress positions
 *       recorded by {@link PlacedBlockTracker}; the farming check is
 *       independent — replanting then harvesting is the intended loop.</li>
 *   <li>{@code UseBlockCallback} — right-click harvest on
 *       {@code lifepath:click_harvest} members when mature.</li>
 *   <li>{@code mixin.BlockItemMixin} — records placements and emits farming
 *       planting events for farmable blocks.</li>
 * </ul>
 */
public final class VanillaGameplayProducers {
	private VanillaGameplayProducers() {
	}

	private static final TagKey<net.minecraft.block.Block> HARVESTABLE =
			TagKey.of(RegistryKeys.BLOCK, LifepathMod.id("harvestable"));
	private static final TagKey<net.minecraft.block.Block> CLICK_HARVEST =
			TagKey.of(RegistryKeys.BLOCK, LifepathMod.id("click_harvest"));
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
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return ActionResult.PASS;
			}
			BlockState state = world.getBlockState(hitResult.getBlockPos());
			// Right-click harvest only for click-harvestables at maturity —
			// CropBlock farming awards on break; PASS always (never consume).
			if (state.isIn(CLICK_HARVEST) && isMature(state)) {
				publishHarvest(serverPlayer, state);
			}
			return ActionResult.PASS;
		});
	}

	private static void onBlockBroken(net.minecraft.world.World world,
			net.minecraft.entity.player.PlayerEntity player, BlockPos pos,
			BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) {
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return;
		}
		boolean wasPlayerPlaced = PlacedBlockTracker.consume(world, pos);
		Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock());
		Set<Identifier> blockTags = state.streamTags()
				.map(TagKey::id).collect(Collectors.toUnmodifiableSet());

		// Mining: only non-player-placed blocks yield events — a block the
		// player placed earlier this session is the re-mine exploit pattern.
		if (!wasPlayerPlaced) {
			ActivityDispatcher.publish(new ActivityEvent(serverPlayer, ActivityTypes.MINING,
					blockId, blockTags, ActivityEvent.Cause.PLAYER, System.currentTimeMillis(),
					Map.of()));
		}

		// Farming harvest: farmable blocks only, mature only — immature
		// harvests emit nothing (spec: unripe crop = exactly 0 XP).
		if (isFarmable(state) && isMature(state)) {
			publishHarvest(serverPlayer, state, blockId, blockTags);
		}
	}

	/** Called by {@code mixin.BlockItemMixin} after a successful block placement. */
	public static void onBlockPlaced(ServerPlayerEntity player, ServerWorld world,
			BlockPos pos, BlockState placedState) {
		PlacedBlockTracker.record(world, pos);
		if (isFarmable(placedState)) {
			Set<Identifier> tags = placedState.streamTags()
					.map(TagKey::id).collect(Collectors.toCollection(HashSet::new));
			tags.add(TAG_PLANTED);
			Identifier blockId = net.minecraft.registry.Registries.BLOCK.getId(placedState.getBlock());
			ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.FARMING,
					blockId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
					System.currentTimeMillis(), Map.of("mature", "false")));
		}
	}

	static boolean isFarmable(BlockState state) {
		return state.isIn(BlockTags.CROPS) || state.isIn(HARVESTABLE) || state.isIn(CLICK_HARVEST);
	}

	/**
	 * Generic maturity: CropBlock.isMature, else a {@code berries} boolean
	 * property (cave vines — checked before age since vines carry both),
	 * else an {@code age} int property at max. No such property → false.
	 */
	static boolean isMature(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMature(state);
		}
		for (Property<?> p : state.getProperties()) {
			if ("berries".equals(p.getName()) && p instanceof BooleanProperty bp) {
				return state.get(bp);
			}
		}
		for (Property<?> p : state.getProperties()) {
			if ("age".equals(p.getName()) && p instanceof IntProperty ip) {
				int max = ip.getValues().stream().mapToInt(Integer::intValue).max().orElse(0);
				return state.get(ip) >= max;
			}
		}
		return false;
	}

	/**
	 * Forge output (anvil / smithing table), called by the screen-handler
	 * mixins before vanilla consumes the inputs. The output item id is the
	 * event sourceId → repetition signatures distinguish identical-recipe
	 * spam for M3-4. All weighting lives in xp_source data.
	 */
	public static void onForgeOutput(net.minecraft.entity.player.PlayerEntity player,
			net.minecraft.item.ItemStack output, Identifier workstationId) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || output.isEmpty()) {
			return;
		}
		Set<Identifier> itemTags = output.streamTags()
				.map(TagKey::id).collect(Collectors.toCollection(HashSet::new));
		Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(output.getItem());
		ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event.ActivityEvents.smithing(
				serverPlayer, itemId, itemTags, workstationId,
				Map.of("count", Integer.toString(output.getCount()))));
	}

	/**
	 * Crafting-table output, called by {@code mixin.CraftingResultSlotMixin}
	 * when the player takes the result stack. The output item id + its tags are
	 * the event surface — all weighting lives in xp_source data.
	 */
	public static void onCraftOutput(net.minecraft.entity.player.PlayerEntity player,
			net.minecraft.item.ItemStack output) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || output.isEmpty()) {
			return;
		}
		Set<Identifier> itemTags = output.streamTags()
				.map(TagKey::id).collect(Collectors.toCollection(HashSet::new));
		Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(output.getItem());
		ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event.ActivityEvents.crafting(
				serverPlayer, itemId, itemTags,
				Map.of("count", Integer.toString(output.getCount())),
				ActivityEvent.Cause.PLAYER));
	}

	/**
	 * Fishing catch, called by {@code mixin.FishingBobberEntityMixin} for each
	 * spawned loot stack. sourceId = caught item id → repetition signatures
	 * distinguish junk spam; caught-item tags carry vanilla/classification
	 * tags ({@code minecraft:fishes}, {@code lifepath:fishing_treasure},
	 * {@code lifepath:fishing_junk}) for data-side weighting.
	 */
	public static void onFishCaught(net.minecraft.entity.player.PlayerEntity player,
			net.minecraft.item.ItemStack caught) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || caught.isEmpty()) {
			return;
		}
		Set<Identifier> itemTags = caught.streamTags()
				.map(TagKey::id).collect(Collectors.toCollection(HashSet::new));
		// Enchanted catches are treasure-pool items even when their item type
		// is also junk-listed (the enchanted fishing_rod overlaps both pools;
		// item tags are component-blind). Treasure must precede junk in the
		// xp_source per_tag order.
		if (caught.hasEnchantments()) {
			itemTags.add(LifepathMod.id("fishing_treasure"));
		}
		Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(caught.getItem());
		ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event.ActivityEvents.fishing(
				serverPlayer, itemId, itemTags,
				Map.of("count", Integer.toString(caught.getCount()))));
	}

	private static void publishHarvest(ServerPlayerEntity player, BlockState state) {
		publishHarvest(player, state,
				net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()),
				state.streamTags().map(TagKey::id).collect(Collectors.toUnmodifiableSet()));
	}

	private static void publishHarvest(ServerPlayerEntity player, BlockState state,
			Identifier blockId, Set<Identifier> blockTags) {
		Set<Identifier> tags = new HashSet<>(blockTags);
		tags.add(TAG_HARVESTED);
		tags.add(TAG_MATURE);
		ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.FARMING,
				blockId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(), Map.of("mature", "true")));
	}

	/** Test hook. */
	static void resetForTests() {
		initialized = false;
	}
}
