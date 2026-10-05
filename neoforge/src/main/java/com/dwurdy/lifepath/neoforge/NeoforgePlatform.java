package com.dwurdy.lifepath.neoforge;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.PlatformServices;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge adapter for the platform seam (M13-4). Installed by
 * {@code NeoforgeLifepath}'s {@code @Mod} constructor before
 * {@code LifepathMod.init()}. Every method bridges onto the NeoForge API —
 * no logic lives here.
 *
 * <p>Payload plumbing on NeoForge fuses codec + receiver registration, so
 * type registrations and handlers are queued during common init and drained
 * inside {@code RegisterPayloadHandlersEvent} on the mod bus.
 */
public final class NeoforgePlatform implements PlatformServices {

	private final DeferredRegister<AttachmentType<?>> attachments =
			DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LifepathMod.MOD_ID);

	private volatile java.util.function.Supplier<AttachmentType<CompoundTag>> characterData;

	// Queued during common init; drained by RegisterPayloadHandlersEvent.
	private final List<PendingType<?>> pendingTypes = new ArrayList<>();

	public NeoforgePlatform(IEventBus modBus) {
		attachments.register(modBus);
		modBus.addListener(RegisterPayloadHandlersEvent.class,
				event -> drainPayloads(event.registrar("1")));
	}

	private record PendingType<T extends CustomPacketPayload>(
			CustomPacketPayload.Type<T> id,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			boolean toServer) {
	}

	private static final class PendingHandlers {
		@Nullable BiConsumer<?, ?> c2s;
		@Nullable BiConsumer<?, ?> clientS2c;
	}

	private final java.util.Map<CustomPacketPayload.Type<?>, PendingHandlers> handlers =
			new java.util.LinkedHashMap<>();

	private void drainPayloads(PayloadRegistrar registrar) {
		for (PendingType<?> pending : pendingTypes) {
			registerTyped(registrar, pending);
		}
		pendingTypes.clear();
	}

	// PayloadRegistrar rejects a second registration of the same type, so each
	// payload registers exactly one direction — the one it was queued with.
	// Handler-less directions (e.g. S2C on a dedicated server) get a no-op.
	private <T extends CustomPacketPayload> void registerTyped(
			PayloadRegistrar registrar, PendingType<T> pending) {
		var pending_ = handlers.get(pending.id());
		if (pending.toServer()) {
			@SuppressWarnings("unchecked")
			BiConsumer<T, ServerPlayer> c2s =
					pending_ == null ? null : (BiConsumer<T, ServerPlayer>) pending_.c2s;
			if (c2s != null) {
				registrar.playToServer(pending.id(), pending.codec(),
						(payload, ctx) -> c2s.accept(payload, (ServerPlayer) ctx.player()));
			} else {
				registrar.playToServer(pending.id(), pending.codec(), (payload, ctx) -> {});
			}
		} else {
			@SuppressWarnings("unchecked")
			BiConsumer<T, Minecraft> s2c =
					pending_ == null ? null : (BiConsumer<T, Minecraft>) pending_.clientS2c;
			if (s2c != null) {
				registrar.playToClient(pending.id(), pending.codec(),
						(payload, ctx) -> ctx.enqueueWork(() ->
								s2c.accept(payload, Minecraft.getInstance())));
			} else {
				registrar.playToClient(pending.id(), pending.codec(), (payload, ctx) -> {});
			}
		}
	}

	/** Called by {@code NeoforgeClientPlatform.onS2C} — queues the client handler. */
	<T extends CustomPacketPayload> void queueClientS2CHandler(
			CustomPacketPayload.Type<T> id, BiConsumer<T, Minecraft> handler) {
		handlers.computeIfAbsent(id, k -> new PendingHandlers()).clientS2c = handler;
	}

	// ---- introspection -------------------------------------------------

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public boolean isAutomation(Player player) {
		return player instanceof net.neoforged.neoforge.common.util.FakePlayer;
	}

	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return ModList.get().getModContainerById(modId)
				.map(c -> c.getModInfo().getVersion().toString());
	}

	/**
	 * NeoForge has no entrypoint mechanism — {@code lifepath:adapter}
	 * adapters are discovered through plain {@link ServiceLoader} instead:
	 * integration mods ship
	 * {@code META-INF/services/com.dwurdy.lifepath.compat.ExternalActivityAdapter}.
	 * The {@code key} parameter is documented on the port; NeoForge ignores
	 * it (the service type is the contract).
	 */
	@Override
	public <T> List<T> entrypoints(String key, Class<T> type) {
		List<T> found = new ArrayList<>();
		ServiceLoader.load(type, getClass().getClassLoader())
				.forEach(found::add);
		return found;
	}

	// ---- persistence ---------------------------------------------------

	@Override
	public void registerCharacterAttachment() {
		if (characterData != null) {
			return;
		}
		characterData = attachments.register("character_data",
				() -> AttachmentType.builder(() -> new CompoundTag())
						.serialize(CompoundTag.CODEC)
						.copyOnDeath()
						.build());
	}

	@Override
	@Nullable
	public CompoundTag getCharacterData(ServerPlayer player) {
		return player.getData(characterData.get());
	}

	@Override
	public void setCharacterData(ServerPlayer player, CompoundTag data) {
		player.setData(characterData.get(), data);
	}

	// ---- networking ----------------------------------------------------

	@Override
	public <T extends CustomPacketPayload> void registerS2C(
			CustomPacketPayload.Type<T> id,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		pendingTypes.add(new PendingType<>(id, codec, false));
	}

	@Override
	public <T extends CustomPacketPayload> void registerC2S(
			CustomPacketPayload.Type<T> id,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		pendingTypes.add(new PendingType<>(id, codec, true));
	}

	@Override
	public <T extends CustomPacketPayload> void onC2S(
			CustomPacketPayload.Type<T> id, BiConsumer<T, ServerPlayer> handler) {
		var slot = handlers.computeIfAbsent(id, k -> new PendingHandlers());
		if (slot.c2s != null) {
			throw new IllegalStateException("duplicate C2S receiver for payload " + id.id());
		}
		slot.c2s = handler;
	}

	@Override
	public void sendTo(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}

	// ---- server lifecycle ----------------------------------------------

	@Override
	public void onEndServerTick(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
				event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStopping(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class,
				event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class,
				event -> listener.accept(event.getServer()));
	}

	// ---- player lifecycle ----------------------------------------------

	@Override
	public void onPlayerJoin(Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class,
				event -> listener.accept((ServerPlayer) event.getEntity()));
	}

	@Override
	public void onPlayerDisconnect(Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class,
				event -> listener.accept((ServerPlayer) event.getEntity()));
	}

	/**
	 * NeoForge's {@code PlayerRespawnEvent} exposes only the new player;
	 * {@code oldPlayer} is the same object pre-copy (NeoForge respawns in
	 * place — there is no second player instance to carry).
	 */
	@Override
	public void onPlayerRespawn(PlayerRespawnListener listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class,
				event -> {
					ServerPlayer player = (ServerPlayer) event.getEntity();
					listener.onRespawn(player, player, event.isEndConquered());
				});
	}

	@Override
	public void onPlayerChangeWorld(PlayerChangeWorldListener listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class,
				event -> {
					ServerPlayer player = (ServerPlayer) event.getEntity();
					var server = player.getServer();
					if (server == null) {
						return;
					}
					listener.onChangeWorld(player,
							server.getLevel(event.getFrom()),
							server.getLevel(event.getTo()));
				});
	}

	// ---- gameplay events -----------------------------------------------

	/**
	 * NeoForge's {@code BlockEvent.BreakEvent} fires pre-break (cancelable)
	 * where Fabric's {@code PlayerBlockBreakEvents.AFTER} is post-break.
	 * Recorded parity gap for #136 — listeners must not mutate block state.
	 */
	@Override
	public void onBlockBreak(BlockBreakListener listener) {
		NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class,
				event -> listener.onBlockBreak(event.getPlayer().level(),
						event.getPlayer(), event.getPos(), event.getState(),
						event.getLevel().getBlockEntity(event.getPos())));
	}

	@Override
	public void onUseBlock(UseBlockListener listener) {
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class,
				event -> {
					InteractionResult result = listener.onUseBlock(event.getEntity(),
							event.getLevel(), event.getHand(), event.getHitVec());
					if (result != InteractionResult.PASS) {
						event.setCancellationResult(result);
						event.setCanceled(true);
					}
				});
	}

	/**
	 * {@code RightClickItem}'s cancellation result is an
	 * {@link InteractionResult} — the stack inside our
	 * {@code InteractionResultHolder} cannot be substituted, recorded on #136.
	 */
	@Override
	public void onUseItem(UseItemListener listener) {
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class,
				event -> {
					var result = listener.onUseItem(event.getEntity(),
							event.getLevel(), event.getHand());
					if (result.getResult() != InteractionResult.PASS) {
						event.setCancellationResult(result.getResult());
						event.setCanceled(true);
					}
				});
	}

	@Override
	public void onEntityKilledOther(EntityKillListener listener) {
		NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, event -> {
			var killer = event.getSource() == null ? null : event.getSource().getEntity();
			if (killer == null || !(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)) {
				return;
			}
			listener.onKilledOther(level, killer, event.getEntity());
		});
	}

	/**
	 * NeoForge's {@code LivingDamageEvent.Post} lacks Fabric's {@code blocked}
	 * flag — {@code getBlockedDamage() > 0} approximates it. Recorded on #136.
	 */
	@Override
	public void onLivingDamage(LivingDamageListener listener) {
		NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class,
				event -> listener.afterDamage(event.getEntity(), event.getSource(),
						event.getOriginalDamage(), event.getNewDamage(),
						event.getBlockedDamage() > 0));
	}

	@Override
	public void onCommandRegistration(CommandRegistrationListener listener) {
		NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
				event -> listener.register(event.getDispatcher(),
						event.getBuildContext(), event.getCommandSelection()));
	}

	// ---- datapack reload -----------------------------------------------

	@Override
	public void registerReloadListener(ResourceLocation id, Consumer<ResourceManager> apply) {
		NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class,
				event -> event.addListener(new SimplePreparableReloadListener<Void>() {
					@Override
					protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
						return null;
					}

					@Override
					protected void apply(Void prepared, ResourceManager manager,
							ProfilerFiller profiler) {
						apply.accept(manager);
					}
				}));
	}
}
