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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

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

	private static final TagKey<net.minecraft.world.level.block.Block> HARVESTABLE =
			TagKey.create(Registries.BLOCK, LifepathMod.id("harvestable"));
	private static final TagKey<net.minecraft.world.level.block.Block> CLICK_HARVEST =
			TagKey.create(Registries.BLOCK, LifepathMod.id("click_harvest"));
	private static final ResourceLocation TAG_PLANTED = LifepathMod.id("planted");
	private static final ResourceLocation TAG_HARVESTED = LifepathMod.id("harvested");
	private static final ResourceLocation TAG_MATURE = LifepathMod.id("mature");
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		PlayerBlockBreakEvents.AFTER.register(VanillaGameplayProducers::onBlockBroken);
		// M8-1: kill events feed the lifepath:combat activity (athletics etc.).
		// The fired entity is the killer — same player-only boundary as
		// block-break (a wolf/golem kill is not the player's exertion).
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY
				.register((world, entity, killedEntity) -> {
					if (!(entity instanceof ServerPlayer killer)) {
						return;
					}
					ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
							.getKey(killedEntity.getType());
					Set<ResourceLocation> entityTags = killedEntity.getType().builtInRegistryHolder()
							.tags().map(TagKey::location)
							.collect(Collectors.toCollection(HashSet::new));
					ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event.ActivityEvents
							.combat(killer, typeId, entityTags, ActivityEvent.Cause.PLAYER));
					// Archery: the same kill also counts as ranged-combat
					// activity when the killing blow was a projectile.
					var killSource = killedEntity.getLastDamageSource();
					if (killSource != null && killSource.is(
							net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
						ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event
								.ActivityEvents.archery(killer, typeId, entityTags,
										ActivityEvent.Cause.PLAYER));
					}
				});
		// M8-3 defence: hostile damage survived is the player's activity;
		// the attacker is the cause. Environment/self damage can't train it.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE
				.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
					if (!(entity instanceof ServerPlayer victim)) {
						return;
					}
					// M9-2: damage-source attunement rules run even without a
					// living attacker (lightning strike, drowning, etc.).
					io.github.durdeuvlad.lifepath.attunement.AttunementService
							.onDamaged(victim, source, victim.level().getRandom(),
									System.currentTimeMillis());
					if (!(source.getEntity() instanceof LivingEntity attacker)
							|| attacker == entity) {
						return;
					}
					ResourceLocation attackerId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
							.getKey(attacker.getType());
					Set<ResourceLocation> attackerTags = attacker.getType().builtInRegistryHolder()
							.tags().map(TagKey::location)
							.collect(Collectors.toCollection(HashSet::new));
					ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event.ActivityEvents
							.defence(victim, attackerId, attackerTags,
									ActivityEvent.Cause.NON_PLAYER));
					// M9-1: attack-type condition acquisition rides the same
					// hostile-hit seam (vampirism bite, lycanthropy scratch).
					io.github.durdeuvlad.lifepath.condition.ConditionService
							.onDamagedBy(victim, attacker, victim.level().getRandom(),
									System.currentTimeMillis());
				});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			BlockState state = world.getBlockState(hitResult.getBlockPos());
			// Right-click harvest only for click-harvestables at maturity —
			// CropBlock farming awards on break; PASS always (never consume).
			if (state.is(CLICK_HARVEST) && isMature(state)) {
				publishHarvest(serverPlayer, state);
			}
			return InteractionResult.PASS;
		});
		// M9-2: attunement ritual items — right-clicking an acquisition item
		// (e.g. phantom membrane for Air) evaluates type:item rules. PASS
		// always; consumption is handled by the service on success.
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT
				.register((player, world, hand) -> {
					if (world.isClientSide()
							|| !(player instanceof ServerPlayer serverPlayer)) {
						return net.minecraft.world.InteractionResultHolder.pass(
								player.getItemInHand(hand));
					}
					io.github.durdeuvlad.lifepath.attunement.AttunementService
							.onUseItem(serverPlayer, serverPlayer.getItemInHand(hand),
									System.currentTimeMillis());
					// M9-4: item-source unlock grants ride the same seam.
					io.github.durdeuvlad.lifepath.unlock.UnlockService
							.onUseItem(serverPlayer, serverPlayer.getItemInHand(hand),
									System.currentTimeMillis());
					return net.minecraft.world.InteractionResultHolder.pass(
							serverPlayer.getItemInHand(hand));
				});
	}

	private static void onBlockBroken(net.minecraft.world.level.Level world,
			net.minecraft.world.entity.player.Player player, BlockPos pos,
			BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		boolean wasPlayerPlaced = PlacedBlockTracker.consume(world, pos);
		ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
		Set<ResourceLocation> blockTags = state.getTags()
				.map(TagKey::location).collect(Collectors.toUnmodifiableSet());

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
	public static void onBlockPlaced(ServerPlayer player, ServerLevel world,
			BlockPos pos, BlockState placedState) {
		PlacedBlockTracker.record(world, pos);
		if (isFarmable(placedState)) {
			Set<ResourceLocation> tags = placedState.getTags()
					.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
			tags.add(TAG_PLANTED);
			ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(placedState.getBlock());
			ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.FARMING,
					blockId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
					System.currentTimeMillis(), Map.of("mature", "false")));
		}
	}

	static boolean isFarmable(BlockState state) {
		return state.is(BlockTags.CROPS) || state.is(HARVESTABLE) || state.is(CLICK_HARVEST);
	}

	/**
	 * Generic maturity: CropBlock.isMature, else a {@code berries} boolean
	 * property (cave vines — checked before age since vines carry both),
	 * else an {@code age} int property at max. No such property → false.
	 */
	static boolean isMature(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		for (Property<?> p : state.getProperties()) {
			if ("berries".equals(p.getName()) && p instanceof BooleanProperty bp) {
				return state.getValue(bp);
			}
		}
		for (Property<?> p : state.getProperties()) {
			if ("age".equals(p.getName()) && p instanceof IntegerProperty ip) {
				int max = ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
				return state.getValue(ip) >= max;
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
	public static void onForgeOutput(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack output, ResourceLocation workstationId) {
		if (!(player instanceof ServerPlayer serverPlayer) || output.isEmpty()) {
			return;
		}
		Set<ResourceLocation> itemTags = output.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
		ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event.ActivityEvents.smithing(
				serverPlayer, itemId, itemTags, workstationId,
				Map.of("count", Integer.toString(output.getCount()))));
	}

	/**
	 * Crafting-table output, called by {@code mixin.CraftingResultSlotMixin}
	 * when the player takes the result stack. The output item id + its tags are
	 * the event surface — all weighting lives in xp_source data.
	 */
	public static void onCraftOutput(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack output) {
		if (!(player instanceof ServerPlayer serverPlayer) || output.isEmpty()) {
			return;
		}
		Set<ResourceLocation> itemTags = output.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
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
	public static void onFishCaught(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack caught) {
		if (!(player instanceof ServerPlayer serverPlayer) || caught.isEmpty()) {
			return;
		}
		Set<ResourceLocation> itemTags = caught.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		// Enchanted catches are treasure-pool items even when their item type
		// is also junk-listed (the enchanted fishing_rod overlaps both pools;
		// item tags are component-blind). Treasure must precede junk in the
		// xp_source per_tag order.
		if (caught.isEnchanted()) {
			itemTags.add(LifepathMod.id("fishing_treasure"));
		}
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(caught.getItem());
		ActivityDispatcher.publish(io.github.durdeuvlad.lifepath.event.ActivityEvents.fishing(
				serverPlayer, itemId, itemTags,
				Map.of("count", Integer.toString(caught.getCount()))));
	}

	private static void publishHarvest(ServerPlayer player, BlockState state) {
		publishHarvest(player, state,
				net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),
				state.getTags().map(TagKey::location).collect(Collectors.toUnmodifiableSet()));
	}

	private static void publishHarvest(ServerPlayer player, BlockState state,
			ResourceLocation blockId, Set<ResourceLocation> blockTags) {
		Set<ResourceLocation> tags = new HashSet<>(blockTags);
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
