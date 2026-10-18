package com.dwurdy.lifepath.compat.create;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.compat.railways.RailwaysPossessedOperator;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.producer.PlacedBlockTracker;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Create "attended automation pays the operator" adapter (NeoForge-only,
 * gated by {@code lifepath.compat.create.mixins.json}'s
 * {@code requiredMods=["create"]}). Policy: unattended automation stays
 * NON_PLAYER — a machine act with no controller emits exactly what it did
 * before (nothing). When a player is actively driving a contraption, the
 * blocks the machine breaks or harvests are re-attributed to that player at
 * the normal producer rates/shapes.
 *
 * <p><b>Why the emit boundary is inside Create, not {@code isAutomation}:</b>
 * verified against Create 6.0.10 bytecode — contraption actors destroy blocks
 * through {@code BlockHelper.destroyBlockAs(level, pos, null, stack, 1f, …)}:
 * the player argument is literally {@code null}, so no NeoForge
 * {@code BlockEvent.BreakEvent} is ever posted and the act never reaches
 * {@code VanillaGameplayProducers}. The automation boundary for mounted
 * actors is therefore {@code MovementBehaviour} itself: drills, saws,
 * ploughs, and rollers all destroy through
 * {@code BlockBreakingMovementBehaviour.destroyBlock(context, pos)} and the
 * mechanical harvester through {@code BlockHelper.destroyBlockAs} inside
 * {@code HarvesterMovementBehaviour.visitNewPosition} (its validity checks
 * already passed at the call site). Deployer-mounted acts are deliberately
 * untouched: a {@code DeployerFakePlayer}'s placer UUID is provenance, not
 * attendance — it keeps hitting {@code isAutomation} and stays NON_PLAYER.
 *
 * <p><b>Operator resolution</b> (all members javap-verified public, so plain
 * {@code publicLookup} handles suffice — resolved lazily, first failure cuts
 * the bridge for the session like {@code SereneSeasonsBridge}):
 * <ol>
 *   <li>{@code context.contraption.entity.getControllingPlayer()} — the
 *       synched {@code CONTROLLED_BY} UUID. Written server-side when a player
 *       grabs a {@code ContraptionControlsBlock} or train controls
 *       ({@code ControlsInteractionBehaviour → setControllingPlayer}), so it
 *       covers bearings/gantries/pistons/elevators AND the carriage hosting
 *       train controls.</li>
 *   <li>Train-wide fallback for actors on OTHER carriages:
 *       {@code CarriageContraptionEntity.getCarriage().train} — scan every
 *       {@code Carriage.anyAvailableEntity()}'s {@code CONTROLLED_BY}, then
 *       {@code Train.backwardsDriver} (the rear-cab conductor Create records
 *       when a controlled train runs backwards). {@code Train.owner} is
 *       assembly provenance — never paid.</li>
 *   <li>UUID → {@code server.getPlayerList().getPlayer(uuid)} + alive.
 *       FakePlayers are never in the player list, and an operator who logs
 *       out mid-drive resolves to {@code null} — the act drops back to the
 *       NON_PLAYER path automatically.</li>
 * </ol>
 *
 * <p><b>Emission</b> mirrors {@code VanillaGameplayProducers.onBlockBroken}:
 * mining for every destroyed block unless {@link PlacedBlockTracker} marks it
 * player-placed (the re-mine exploit applies to machine-placed bait too), plus
 * the canonical farming harvest event for mature farmable blocks. The
 * dedicated harvester emits farming only. {@code isFarmable}/{@code isMature}
 * are replicated 1:1 from the producers — they are package-private there and
 * this adapter must stay read-only toward them.
 */
public final class CreateAttendedAutomation {
	private CreateAttendedAutomation() {
	}

	// ---- foreign members (Create 6.0.10, Mojmap names) -------------------

	private static final String MOVEMENT_CONTEXT =
			"com.simibubi.create.content.contraptions.behaviour.MovementContext";
	private static final String CONTRAPTION =
			"com.simibubi.create.content.contraptions.Contraption";
	private static final String CONTRAPTION_ENTITY =
			"com.simibubi.create.content.contraptions.AbstractContraptionEntity";
	private static final String CARRIAGE_ENTITY =
			"com.simibubi.create.content.trains.entity.CarriageContraptionEntity";
	private static final String CARRIAGE =
			"com.simibubi.create.content.trains.entity.Carriage";
	private static final String TRAIN =
			"com.simibubi.create.content.trains.entity.Train";

	private static volatile boolean resolved;
	private static volatile boolean cut;
	@Nullable private static Class<?> carriageEntityClass;
	@Nullable private static MethodHandle worldGet;              // MovementContext → Level
	@Nullable private static MethodHandle contraptionGet;        // MovementContext → Contraption
	@Nullable private static MethodHandle entityGet;             // Contraption → AbstractContraptionEntity
	@Nullable private static MethodHandle getControllingPlayer;  // AbstractContraptionEntity → Optional<UUID>
	@Nullable private static MethodHandle getCarriage;           // CarriageContraptionEntity → Carriage
	@Nullable private static MethodHandle trainGet;              // Carriage → Train
	@Nullable private static MethodHandle anyAvailableEntity;    // Carriage → CarriageContraptionEntity
	@Nullable private static MethodHandle carriagesGet;          // Train → List<Carriage>
	@Nullable private static MethodHandle backwardsDriverGet;    // Train → Player

	// ---- entry points (called by the Create mixins) ----------------------

	/**
	 * {@code BlockBreakingMovementBehaviour.destroyBlock(context, pos)} HEAD —
	 * one call per block the machine actually destroys (drill/saw trigger
	 * blocks and the roller's extra column positions; the destroy itself can
	 * no longer be cancelled once entered). Emits the same events a player
	 * block-break would.
	 */
	public static void onContraptionBlockBroken(Object movementContext, BlockPos pos) {
		Level level = levelOf(movementContext);
		if (!(level instanceof ServerLevel)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		// Consume like a player break would — one recorded placement defeats
		// one break, whoever (or whatever) breaks it.
		boolean wasPlayerPlaced = PlacedBlockTracker.consume(level, pos);
		boolean farmableHarvest = isFarmable(state) && isMature(state);
		if (wasPlayerPlaced && !farmableHarvest) {
			return;
		}
		ServerPlayer operator = resolveOperator(movementContext);
		if (operator == null) {
			return;
		}
		ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		Set<ResourceLocation> blockTags = state.getTags()
				.map(TagKey::location).collect(Collectors.toUnmodifiableSet());
		if (!wasPlayerPlaced) {
			ActivityDispatcher.publish(new ActivityEvent(operator, ActivityTypes.MINING,
					blockId, blockTags, ActivityEvent.Cause.PLAYER,
					System.currentTimeMillis(), Map.of()));
		}
		if (farmableHarvest) {
			publishHarvest(operator, blockId, blockTags);
		}
	}

	/**
	 * {@code HarvesterMovementBehaviour.visitNewPosition} at its
	 * {@code destroyBlockAs} INVOKE — fired only after Create's own crop
	 * validity checks passed, while the pre-harvest state is still in the
	 * world. The harvester is a dedicated harvesting act → farming only, and
	 * only for blocks that satisfy the same farmable+mature rule the
	 * producers apply (an unripe or non-farmable catch — sugar cane, leaves —
	 * pays nothing, matching the player spec of 0 XP for unripe harvests).
	 */
	public static void onContraptionHarvest(Object movementContext, BlockPos pos) {
		Level level = levelOf(movementContext);
		if (!(level instanceof ServerLevel)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!isFarmable(state) || !isMature(state)) {
			return;
		}
		ServerPlayer operator = resolveOperator(movementContext);
		if (operator == null) {
			return;
		}
		publishHarvest(operator, BuiltInRegistries.BLOCK.getKey(state.getBlock()),
				state.getTags().map(TagKey::location)
						.collect(Collectors.toUnmodifiableSet()));
	}

	// ---- operator resolution ----------------------------------------------

	/** {@code context.world} — the level the actor is operating in. */
	@Nullable
	private static Level levelOf(Object movementContext) {
		if (movementContext == null || !resolve()) {
			return null;
		}
		try {
			return (Level) worldGet.invoke(movementContext);
		} catch (Throwable t) {
			cutOnce(t);
			return null;
		}
	}

	/**
	 * The player currently driving this context's contraption, or null —
	 * null means "unattended" and the caller must emit nothing (the existing
	 * NON_PLAYER behaviour is to produce no player event at all).
	 */
	@Nullable
	private static ServerPlayer resolveOperator(Object movementContext) {
		if (movementContext == null || !resolve()) {
			return null;
		}
		try {
			Object contraption = contraptionGet.invoke(movementContext);
			if (contraption == null) {
				return null;
			}
			Object entity = entityGet.invoke(contraption);
			if (!(entity instanceof Entity e)) {
				return null;
			}
			MinecraftServer server = e.getServer();
			if (server == null) {
				return null;
			}
			ServerPlayer operator = controlledPlayer(server, entity);
			if (operator != null || carriageEntityClass == null
					|| !carriageEntityClass.isInstance(entity)) {
				return operator;
			}
			// Train path: the controls can sit on a different carriage than
			// the actor — CONTROLLED_BY lives per-entity, so scan all of the
			// train's present entities, then the recorded rear-cab driver.
			Object carriage = getCarriage.invoke(entity);
			Object train = carriage == null ? null : trainGet.invoke(carriage);
			if (train == null) {
				return null;
			}
			for (Object c : (List<?>) carriagesGet.invoke(train)) {
				Object other = anyAvailableEntity.invoke(c);
				if (other instanceof Entity otherEntity) {
					operator = controlledPlayer(server, otherEntity);
					if (operator != null) {
						return operator;
					}
				}
			}
			Object driver = backwardsDriverGet.invoke(train);
			if (driver instanceof Player playerDriver) {
				return onlineAlive(server, playerDriver.getUUID());
			}
			return null;
		} catch (Throwable t) {
			cutOnce(t);
			return null;
		}
	}

	@Nullable
	private static ServerPlayer controlledPlayer(MinecraftServer server, Object entity)
			throws Throwable {
		Object uuid = ((Optional<?>) getControllingPlayer.invoke(entity)).orElse(null);
		if (!(uuid instanceof UUID id)) {
			return null;
		}
		ServerPlayer player = onlineAlive(server, id);
		// Steam 'n' Rails: CONTROLLED_BY can hold a conductor golem's shared
		// FakePlayer UUID while a real player drives the train through
		// possession — resolve through the drive-through record the railways
		// seam keeps per carriage (empty/inert without railways).
		return player != null ? player
				: RailwaysPossessedOperator.possessedOperator(server, entity);
	}

	/** getPlayerList already excludes FakePlayers and offline UUIDs; isAlive covers mid-respawn. */
	@Nullable
	private static ServerPlayer onlineAlive(MinecraftServer server, UUID uuid) {
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		return player != null && player.isAlive() ? player : null;
	}

	// ---- emit --------------------------------------------------------------

	/** Same shape as {@code VanillaGameplayProducers#publishHarvest}. */
	private static void publishHarvest(ServerPlayer player, ResourceLocation blockId,
			Set<ResourceLocation> blockTags) {
		Set<ResourceLocation> tags = new HashSet<>(blockTags);
		tags.add(TAG_HARVESTED);
		tags.add(TAG_MATURE);
		ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.FARMING,
				blockId, Set.copyOf(tags), ActivityEvent.Cause.PLAYER,
				System.currentTimeMillis(), Map.of("mature", "true")));
	}

	// ---- farmable/mature — 1:1 mirror of VanillaGameplayProducers -----------

	private static final TagKey<Block> HARVESTABLE =
			TagKey.create(Registries.BLOCK, LifepathMod.id("harvestable"));
	private static final TagKey<Block> CLICK_HARVEST =
			TagKey.create(Registries.BLOCK, LifepathMod.id("click_harvest"));
	private static final ResourceLocation TAG_HARVESTED = LifepathMod.id("harvested");
	private static final ResourceLocation TAG_MATURE = LifepathMod.id("mature");

	private static boolean isFarmable(BlockState state) {
		return state.is(BlockTags.CROPS) || state.is(HARVESTABLE) || state.is(CLICK_HARVEST);
	}

	private static boolean isMature(BlockState state) {
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

	// ---- lazy bridge ---------------------------------------------------------

	/**
	 * One-shot resolution of the foreign members. All targets are public
	 * members of public classes, so {@code publicLookup} is enough; any
	 * mismatch (a Create update renaming something) flips {@link #cut} once
	 * and every later query fails closed to "unattended".
	 */
	private static boolean resolve() {
		if (cut) {
			return false;
		}
		if (resolved) {
			return true;
		}
		synchronized (CreateAttendedAutomation.class) {
			if (resolved || cut) {
				return !cut;
			}
			try {
				Class<?> mc = Class.forName(MOVEMENT_CONTEXT);
				Class<?> contraption = Class.forName(CONTRAPTION);
				Class<?> ace = Class.forName(CONTRAPTION_ENTITY);
				Class<?> cce = Class.forName(CARRIAGE_ENTITY);
				Class<?> carriage = Class.forName(CARRIAGE);
				Class<?> train = Class.forName(TRAIN);
				MethodHandles.Lookup lk = MethodHandles.publicLookup();
				worldGet = lk.findGetter(mc, "world", Level.class);
				contraptionGet = lk.findGetter(mc, "contraption", contraption);
				entityGet = lk.findGetter(contraption, "entity", ace);
				getControllingPlayer = lk.findVirtual(ace, "getControllingPlayer",
						MethodType.methodType(Optional.class));
				getCarriage = lk.findVirtual(cce, "getCarriage",
						MethodType.methodType(carriage));
				trainGet = lk.findGetter(carriage, "train", train);
				anyAvailableEntity = lk.findVirtual(carriage, "anyAvailableEntity",
						MethodType.methodType(cce));
				carriagesGet = lk.findGetter(train, "carriages", List.class);
				backwardsDriverGet = lk.findGetter(train, "backwardsDriver", Player.class);
				carriageEntityClass = cce;
				resolved = true;
				LifepathMod.LOGGER.info(
						"Create attended-automation bridge active — controlled "
								+ "contraptions pay their operator");
				return true;
			} catch (Throwable t) {
				cutOnce(t);
				return false;
			}
		}
	}

	private static void cutOnce(Throwable t) {
		if (!cut) {
			cut = true;
			LifepathMod.LOGGER.warn(
					"Create attended-automation bridge cut — {} ({}). Machine "
							+ "acts stay NON_PLAYER for the session.",
					t.getClass().getSimpleName(), t.getMessage());
		}
	}
}
