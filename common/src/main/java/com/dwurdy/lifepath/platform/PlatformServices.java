package com.dwurdy.lifepath.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The single port every loader adapter implements (ADR: docs/M13-ADR.md).
 * Common code asks; loaders answer. Method names describe the domain event or
 * capability, never the underlying loader API.
 */
public interface PlatformServices {

	// ---- introspection -------------------------------------------------

	/** Whether the mod with the given id is loaded in this instance. */
	boolean isModLoaded(String modId);

	/**
	 * True for machine-driven players — Create deployers, fake players from
	 * loader APIs — so gameplay producers can refuse to attribute activity to
	 * them. {@code instanceof ServerPlayer} alone is not enough: loader
	 * FakePlayers extend it.
	 */
	boolean isAutomation(Player player);

	/** The loader's config directory (e.g. {@code run/config}). */
	Path configDir();

	/** The loaded version string of a mod, if present. */
	Optional<String> modVersion(String modId);

	/**
	 * Cross-mod integration discovery — adapters declared by other mods (and
	 * by Lifepath itself) under the namespaced key. Fabric: entrypoints;
	 * NeoForge (M13-4): an equivalent annotation/registration scan.
	 */
	<T> List<T> entrypoints(String key, Class<T> type);

	// ---- persistence ---------------------------------------------------

	/**
	 * Registers the {@code lifepath:character_data} persistent player-data
	 * store: survives death/respawn, logout, restarts and dimension changes.
	 * Called once during bootstrap; must be idempotent.
	 */
	void registerCharacterAttachment();

	/** Raw stored blob for the player, or {@code null}/empty when absent. */
	@Nullable
	CompoundTag getCharacterData(ServerPlayer player);

	/** Stores the blob; overwrite semantics. */
	void setCharacterData(ServerPlayer player, CompoundTag data);

	// ---- networking ----------------------------------------------------

	/** Registers a server → client payload codec. Call during mod init. */
	<T extends CustomPacketPayload> void registerS2C(
			CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

	/** Registers a client → server payload codec. Call during mod init. */
	<T extends CustomPacketPayload> void registerC2S(
			CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

	/**
	 * Registers the server-side receiver for a C2S type.
	 *
	 * @throws IllegalStateException if a receiver is already registered for the
	 *         type (duplicate registration is a bug — the first would silently win)
	 */
	<T extends CustomPacketPayload> void onC2S(
			CustomPacketPayload.Type<T> id, BiConsumer<T, ServerPlayer> handler);

	/** Sends an S2C payload to one player. */
	void sendTo(ServerPlayer player, CustomPacketPayload payload);

	// ---- server lifecycle ----------------------------------------------

	void onEndServerTick(Consumer<MinecraftServer> listener);

	void onServerStopping(Consumer<MinecraftServer> listener);

	void onServerStopped(Consumer<MinecraftServer> listener);

	// ---- player lifecycle ----------------------------------------------

	void onPlayerJoin(Consumer<ServerPlayer> listener);

	void onPlayerDisconnect(Consumer<ServerPlayer> listener);

	/** Fires after a player respawns; {@code alive} mirrors the loader's end-convention. */
	void onPlayerRespawn(PlayerRespawnListener listener);

	/** Fires after a player changes dimension. */
	void onPlayerChangeWorld(PlayerChangeWorldListener listener);

	// ---- gameplay events -----------------------------------------------

	/** Fires after a player breaks a block (automation never fires it). */
	void onBlockBreak(BlockBreakListener listener);

	/**
	 * Right-click-block interception; returning anything but
	 * {@link InteractionResult#PASS} consumes the interaction.
	 */
	void onUseBlock(UseBlockListener listener);

	/**
	 * Right-click item-use interception; returning a non-pass holder consumes
	 * the interaction.
	 */
	void onUseItem(UseItemListener listener);

	/** Fires after an entity kills another entity (killer-first). */
	void onEntityKilledOther(EntityKillListener listener);

	/** Fires after a living entity takes damage (post-mitigation). */
	void onLivingDamage(LivingDamageListener listener);

	/** Command registration hook — fired when the dispatcher is built. */
	void onCommandRegistration(CommandRegistrationListener listener);

	// ---- datapack reload -----------------------------------------------

	/** Synchronous datapack-reload hook; {@code apply} receives the live manager. */
	void registerReloadListener(ResourceLocation id, Consumer<ResourceManager> apply);

	// ---- listener shapes ------------------------------------------------

	@FunctionalInterface
	interface PlayerRespawnListener {
		void onRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
	}

	@FunctionalInterface
	interface PlayerChangeWorldListener {
		void onChangeWorld(ServerPlayer player, ServerLevel origin, ServerLevel destination);
	}

	@FunctionalInterface
	interface BlockBreakListener {
		void onBlockBreak(Level level, Player player, BlockPos pos,
				BlockState state, @Nullable BlockEntity blockEntity);
	}

	@FunctionalInterface
	interface UseBlockListener {
		InteractionResult onUseBlock(Player player, Level level,
				InteractionHand hand, BlockHitResult hitResult);
	}

	@FunctionalInterface
	interface UseItemListener {
		InteractionResultHolder<ItemStack> onUseItem(Player player, Level level,
				InteractionHand hand);
	}

	@FunctionalInterface
	interface EntityKillListener {
		void onKilledOther(ServerLevel level, Entity killer, LivingEntity killed);
	}

	@FunctionalInterface
	interface LivingDamageListener {
		void afterDamage(LivingEntity entity, DamageSource source,
				float baseDamageTaken, float damageTaken, boolean blocked);
	}

	@FunctionalInterface
	interface CommandRegistrationListener {
		void register(CommandDispatcher<CommandSourceStack> dispatcher,
				CommandBuildContext buildContext, Commands.CommandSelection environment);
	}
}
