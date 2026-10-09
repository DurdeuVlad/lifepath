package com.dwurdy.lifepath.producer;

import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.LifepathMod;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.dwurdy.lifepath.platform.Platform;
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

	/**
	 * True for machine-driven players (Create deployers, loader FakePlayers)
	 * — they pass {@code instanceof ServerPlayer}, so every player-gated
	 * producer must check this before attributing activity. Uninitialized
	 * platform (tests) conservatively means "real".
	 */
	private static boolean isAutomation(net.minecraft.world.entity.player.Player player) {
		return com.dwurdy.lifepath.platform.Platform.isInitialized()
				&& com.dwurdy.lifepath.platform.Platform.get()
						.isAutomation(player);
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
		Platform.get().onBlockBreak(VanillaGameplayProducers::onBlockBroken);
		// M8-1: kill events feed the lifepath:combat activity (athletics etc.).
		// The fired entity is the killer — same player-only boundary as
		// block-break (a wolf/golem kill is not the player's exertion).
		Platform.get().onEntityKilledOther((world, entity, killedEntity) -> {
					if (!(entity instanceof ServerPlayer killer)
							|| isAutomation(killer)) {
						return;
					}
					ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
							.getKey(killedEntity.getType());
					Set<ResourceLocation> entityTags = killedEntity.getType().builtInRegistryHolder()
							.tags().map(TagKey::location)
							.collect(Collectors.toCollection(HashSet::new));
					ActivityDispatcher.publish(com.dwurdy.lifepath.event.ActivityEvents
							.combat(killer, typeId, entityTags, ActivityEvent.Cause.PLAYER));
					// Archery: the same kill also counts as ranged-combat
					// activity when the killing blow was a projectile.
					var killSource = killedEntity.getLastDamageSource();
					if (killSource != null && killSource.is(
							net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
						ActivityDispatcher.publish(com.dwurdy.lifepath.event
								.ActivityEvents.archery(killer, typeId, entityTags,
										ActivityEvent.Cause.PLAYER));
					}
				});
		// M8-3 defence: hostile damage survived is the player's activity;
		// the attacker is the cause. Environment/self damage can't train it.
		Platform.get().onLivingDamage(
				(entity, source, baseDamageTaken, damageTaken, blocked) -> {
					if (!(entity instanceof ServerPlayer victim)
							|| isAutomation(victim)) {
						return;
					}
					// M9-2: damage-source attunement rules run even without a
					// living attacker (lightning strike, drowning, etc.).
					com.dwurdy.lifepath.attunement.AttunementService
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
					ActivityDispatcher.publish(com.dwurdy.lifepath.event.ActivityEvents
							.defence(victim, attackerId, attackerTags,
									ActivityEvent.Cause.NON_PLAYER));
					// M9-1: attack-type condition acquisition rides the same
					// hostile-hit seam (vampirism bite, lycanthropy scratch).
					com.dwurdy.lifepath.condition.ConditionService
							.onDamagedBy(victim, attacker, victim.level().getRandom(),
									System.currentTimeMillis());
				});
		Platform.get().onUseBlock((player, world, hand, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
					|| isAutomation(serverPlayer)) {
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
		Platform.get().onUseItem((player, world, hand) -> {
					if (world.isClientSide()
							|| !(player instanceof ServerPlayer serverPlayer)
							|| isAutomation(serverPlayer)) {
						return net.minecraft.world.InteractionResultHolder.pass(
								player.getItemInHand(hand));
					}
					com.dwurdy.lifepath.attunement.AttunementService
							.onUseItem(serverPlayer, serverPlayer.getItemInHand(hand),
									System.currentTimeMillis());
					// M9-4: item-source unlock grants ride the same seam.
					com.dwurdy.lifepath.unlock.UnlockService
							.onUseItem(serverPlayer, serverPlayer.getItemInHand(hand),
									System.currentTimeMillis());
					// Beta-10: diet-allowed non-food items (automaton
					// ingots) consume on right-click — vanilla has no eat
					// path for items without a FOOD component.
					net.minecraft.world.item.ItemStack held =
							serverPlayer.getItemInHand(hand);
					if (com.dwurdy.lifepath.species.DietService
							.tryEatDietItem(serverPlayer, held)) {
						return net.minecraft.world.InteractionResultHolder
								.consume(held);
					}
					return net.minecraft.world.InteractionResultHolder.pass(
							serverPlayer.getItemInHand(hand));
				});
	}

	private static void onBlockBroken(net.minecraft.world.level.Level world,
			net.minecraft.world.entity.player.Player player, BlockPos pos,
			BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(player)) {
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
		if (isAutomation(player)) {
			return;
		}
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
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(player)
				|| output.isEmpty()) {
			return;
		}
		Set<ResourceLocation> itemTags = output.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
		ActivityDispatcher.publish(com.dwurdy.lifepath.event.ActivityEvents.smithing(
				serverPlayer, itemId, itemTags, workstationId,
				Map.of("count", Integer.toString(output.getCount()))));
	}

	/**
	 * M22: screen-handler takes (anvil, smithing table) — the taken stack is
	 * mutated in place by outcome scaling. Overgeared calls {@link
	 * #onForgeOutput} directly instead (its output drops into the world; the
	 * mixin applies scaling to the spawned ItemEntity).
	 */
	public static void onForgeTake(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack output, ResourceLocation workstationId) {
		onForgeOutput(player, output, workstationId);
		if (!(player instanceof ServerPlayer serverPlayer) || output.isEmpty()) {
			return;
		}
		Set<ResourceLocation> tags = output.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		tags.add(workstationId);
		tags.add(com.dwurdy.lifepath.LifepathMod.id("smithing_workstations"));
		com.dwurdy.lifepath.skill.OutcomeService.apply(serverPlayer,
				com.dwurdy.lifepath.event.ActivityTypes.SMITHING,
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem()),
				tags, output);
	}

	/**
	 * Crafting-table output, called by {@code mixin.CraftingResultSlotMixin}
	 * when the player takes the result stack. The output item id + its tags are
	 * the event surface — all weighting lives in xp_source data.
	 */
	public static void onCraftOutput(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack output) {
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(player)
				|| output.isEmpty()) {
			return;
		}
		Set<ResourceLocation> itemTags = output.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem());
		ActivityDispatcher.publish(com.dwurdy.lifepath.event.ActivityEvents.crafting(
				serverPlayer, itemId, itemTags,
				Map.of("count", Integer.toString(output.getCount())),
				ActivityEvent.Cause.PLAYER));
		// M22: outcome scaling mutates the taken stack (count/quality/failure).
		com.dwurdy.lifepath.skill.OutcomeService.apply(serverPlayer,
				com.dwurdy.lifepath.event.ActivityTypes.CRAFTING, itemId, itemTags, output);
	}

	/**
	 * Furnace/smoker/blast-furnace output, called by {@code
	 * mixin.FurnaceResultSlotMixin}. Only edible outputs feed the cooking
	 * loop — ore smelting must not earn crafting XP or trip cooking
	 * outcome rules.
	 */
	public static void onFurnaceCookOutput(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack output) {
		if (output.isEmpty()
				|| !output.has(net.minecraft.core.component.DataComponents.FOOD)) {
			return;
		}
		onCraftOutput(player, output);
	}

	/**
	 * Brewing-stand potion take-out, called by {@code
	 * mixin.BrewingStandSlotMixin} for output slots 0–2. Tags carry
	 * {@code lifepath:brewed} plus {@code lifepath:potion/<effect_id>} so
	 * xp_source data can pay more for strong potions. The taken stack also
	 * gets outcome-scaled (brewing botch/quality bands).
	 */
	public static void onBrewingOutput(net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack output) {
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(player)
				|| output.isEmpty()
				|| !output.has(net.minecraft.core.component.DataComponents.POTION_CONTENTS)) {
			return;
		}
		Set<ResourceLocation> itemTags = output.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		itemTags.add(LifepathMod.id("brewed"));
		var contents = output.get(
				net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		contents.potion().flatMap(h -> h.unwrapKey())
				.ifPresent(key -> itemTags.add(
						LifepathMod.id("potion/" + key.location().getPath())));
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getKey(output.getItem());
		ActivityDispatcher.publish(com.dwurdy.lifepath.event.ActivityEvents.brewing(
				serverPlayer, itemId, itemTags, ActivityEvent.Cause.PLAYER));
		com.dwurdy.lifepath.skill.OutcomeService.apply(serverPlayer,
				com.dwurdy.lifepath.event.ActivityTypes.BREWING, itemId,
				itemTags, output);
	}

	/**
	 * Fishing catch, called by {@code mixin.FishingBobberEntityMixin} for each
	 * spawned loot stack. sourceId = caught item id → repetition signatures
	 * distinguish junk spam; caught-item tags carry vanilla/classification
	 * tags ({@code minecraft:fishes}, {@code lifepath:fishing_treasure},
	 * {@code lifepath:fishing_junk}) for data-side weighting.
	 */
	public static net.minecraft.world.item.ItemStack onFishCaught(
			net.minecraft.world.entity.player.Player player,
			net.minecraft.world.item.ItemStack caught) {
		if (!(player instanceof ServerPlayer serverPlayer) || isAutomation(player)
				|| caught.isEmpty()) {
			return caught;
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
		ActivityDispatcher.publish(com.dwurdy.lifepath.event.ActivityEvents.fishing(
				serverPlayer, itemId, itemTags,
				Map.of("count", Integer.toString(caught.getCount()))));
		// M24: outcome scaling on the catch — count/quality, and at high bands
		// junk can upgrade to a real fish (junk_upgrade_chance).
		var applied = com.dwurdy.lifepath.skill.OutcomeService.apply(serverPlayer,
				com.dwurdy.lifepath.event.ActivityTypes.FISHING, itemId, itemTags, caught);
		if (applied.outcome().junkUpgradeChance() > 0
				&& caught.is(FISHING_JUNK)
				&& serverPlayer.getRandom().nextDouble()
						< applied.outcome().junkUpgradeChance()) {
			var fishes = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getTag(net.minecraft.tags.ItemTags.FISHES);
			if (fishes.isPresent()) {
				var list = fishes.get().stream().toList();
				var pick = list.get(serverPlayer.getRandom().nextInt(list.size()));
				var upgraded = new net.minecraft.world.item.ItemStack(pick, caught.getCount());
				com.dwurdy.lifepath.skill.OutcomeService.apply(serverPlayer,
						com.dwurdy.lifepath.event.ActivityTypes.FISHING,
						net.minecraft.core.registries.BuiltInRegistries.ITEM
								.getKey(upgraded.getItem()),
						upgraded.getTags().map(TagKey::location)
								.collect(Collectors.toCollection(HashSet::new)),
						upgraded);
				return upgraded;
			}
		}
		return caught;
	}

	private static final TagKey<net.minecraft.world.item.Item> FISHING_JUNK =
			TagKey.create(net.minecraft.core.registries.Registries.ITEM,
					LifepathMod.id("fishing_junk"));

	/**
	 * Snapshot of "was this position player-placed" taken at
	 * {@code destroyBlock} HEAD — before the XP path's
	 * {@code PlacedBlockTracker.consume} runs (NeoForge {@code BreakEvent}
	 * fires pre-break, so without the snapshot the mark is already gone by
	 * the time {@code getDrops} runs). Keyed by player; a break produces at
	 * most one drop batch so the flag is consumed on first read.
	 */
	private static final java.util.Map<java.util.UUID, Boolean> placedAtBreak =
			new java.util.HashMap<>();

	/** Called by {@code mixin.GameModeOutcomeMixin} at destroyBlock HEAD. */
	public static void onDestroyBlockStart(ServerPlayer player, BlockPos pos) {
		placedAtBreak.put(player.getUUID(),
				PlacedBlockTracker.contains(player.level(), pos));
	}

	/**
	 * Block-break drops — called by {@code mixin.BlockDropOutcomeMixin} from
	 * {@code Block.getDrops} when the breaker is a real player. Re-uses the
	 * mining/farming discrimination of the XP producer: farmable+mature
	 * blocks resolve against FARMING, everything else against MINING, and
	 * placed blocks get no yield scaling (re-mine exploit guard — reads the
	 * HEAD-time snapshot; drops generated outside destroyBlock fall back to
	 * a non-consuming tracker peek).
	 */
	public static void onBlockDrops(ServerPlayer player, BlockPos pos,
			BlockState state, java.util.List<net.minecraft.world.item.ItemStack> drops) {
		if (isAutomation(player) || drops.isEmpty()) {
			return;
		}
		Boolean placed = placedAtBreak.remove(player.getUUID());
		if (placed == null ? PlacedBlockTracker.contains(player.level(), pos)
				: placed) {
			return;
		}
		ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getKey(state.getBlock());
		Set<ResourceLocation> blockTags = state.getTags()
				.map(TagKey::location).collect(Collectors.toCollection(HashSet::new));
		ResourceLocation activity = isFarmable(state) && isMature(state)
				? com.dwurdy.lifepath.event.ActivityTypes.FARMING
				: com.dwurdy.lifepath.event.ActivityTypes.MINING;
		com.dwurdy.lifepath.skill.OutcomeService.applyDrops(player, activity,
				blockId, blockTags, drops);
	}

	/**
	 * Entity kill drops — called by {@code mixin.EntityLootOutcomeMixin},
	 * which wraps the loot consumer inside
	 * {@code LivingEntity.dropFromLootTable}. The victim is the dying entity;
	 * the credited player is the damage source's causing entity (the shooter
	 * for projectile kills — {@code getEntity()} returns the shooter, not the
	 * arrow). Projectile kills resolve ARCHERY, everything else COMBAT; the
	 * victim's entity-type tags are the match surface (hunting rules key on
	 * {@code lifepath:animals}). Each produced stack is scaled in place and
	 * dropped entirely when its count reaches zero.
	 */
	public static java.util.function.Consumer<net.minecraft.world.item.ItemStack>
			wrapKillDropConsumer(net.minecraft.world.entity.LivingEntity victim,
			net.minecraft.world.damagesource.DamageSource source,
			java.util.function.Consumer<net.minecraft.world.item.ItemStack> consumer) {
		if (victim instanceof net.minecraft.world.entity.player.Player
				|| !(source.getEntity() instanceof ServerPlayer player)
				|| isAutomation(player)) {
			return consumer;
		}
		ResourceLocation victimId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
				.getKey(victim.getType());
		Set<ResourceLocation> tags = victim.getType().builtInRegistryHolder()
				.tags().map(TagKey::location)
				.collect(Collectors.toCollection(HashSet::new));
		ResourceLocation activity = source.is(
				net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
				? com.dwurdy.lifepath.event.ActivityTypes.ARCHERY
				: com.dwurdy.lifepath.event.ActivityTypes.COMBAT;
		var outcome = com.dwurdy.lifepath.skill.OutcomeService.resolveForPlayer(
				player, activity, victimId, tags);
		if (outcome == com.dwurdy.lifepath.skill.OutcomeService.Outcome.IDENTITY) {
			return consumer;
		}
		return stack -> {
			if (com.dwurdy.lifepath.skill.OutcomeService.applyDrop(player, outcome, stack)) {
				consumer.accept(stack);
			}
		};
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
