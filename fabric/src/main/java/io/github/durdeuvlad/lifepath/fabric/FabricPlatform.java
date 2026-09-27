package io.github.durdeuvlad.lifepath.fabric;

import io.github.durdeuvlad.lifepath.LifepathMod;
import io.github.durdeuvlad.lifepath.platform.PlatformServices;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric adapter for the platform seam (M13-3). Installed by
 * {@code FabricLifepath#onInitialize} before {@code LifepathMod.init()}.
 * Every method is a one-to-one bridge onto the Fabric API — no logic lives
 * here.
 */
public final class FabricPlatform implements PlatformServices {

	// ---- introspection -------------------------------------------------

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(c -> c.getMetadata().getVersion().getFriendlyString());
	}

	@Override
	public <T> List<T> entrypoints(String key, Class<T> type) {
		return FabricLoader.getInstance().getEntrypoints(key, type);
	}

	// ---- persistence ---------------------------------------------------

	private AttachmentType<CompoundTag> characterData;

	@Override
	public void registerCharacterAttachment() {
		if (characterData != null) {
			return;
		}
		characterData = AttachmentRegistry.<CompoundTag>builder()
				.persistent(CompoundTag.CODEC)
				.copyOnDeath()
				.initializer(CompoundTag::new)
				.buildAndRegister(LifepathMod.id("character_data"));
	}

	@Override
	@Nullable
	public CompoundTag getCharacterData(ServerPlayer player) {
		return player.getAttached(characterData);
	}

	@Override
	public void setCharacterData(ServerPlayer player, CompoundTag data) {
		player.setAttached(characterData, data);
	}

	// ---- networking ----------------------------------------------------

	@Override
	public <T extends CustomPacketPayload> void registerS2C(
			CustomPacketPayload.Type<T> id,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.playS2C().register(id, codec);
	}

	@Override
	public <T extends CustomPacketPayload> void registerC2S(
			CustomPacketPayload.Type<T> id,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.playC2S().register(id, codec);
	}

	@Override
	public <T extends CustomPacketPayload> void onC2S(
			CustomPacketPayload.Type<T> id, BiConsumer<T, ServerPlayer> handler) {
		if (!ServerPlayNetworking.registerGlobalReceiver(id,
				(payload, context) -> handler.accept(payload, context.player()))) {
			throw new IllegalStateException("duplicate C2S receiver for payload " + id.id());
		}
	}

	@Override
	public void sendTo(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	// ---- server lifecycle ----------------------------------------------

	@Override
	public void onEndServerTick(Consumer<MinecraftServer> listener) {
		ServerTickEvents.END_SERVER_TICK.register(listener::accept);
	}

	@Override
	public void onServerStopping(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STOPPING.register(listener::accept);
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STOPPED.register(listener::accept);
	}

	// ---- player lifecycle ----------------------------------------------

	@Override
	public void onPlayerJoin(Consumer<ServerPlayer> listener) {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				listener.accept(handler.getPlayer()));
	}

	@Override
	public void onPlayerDisconnect(Consumer<ServerPlayer> listener) {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				listener.accept(handler.getPlayer()));
	}

	@Override
	public void onPlayerRespawn(PlayerRespawnListener listener) {
		ServerPlayerEvents.AFTER_RESPAWN.register(listener::onRespawn);
	}

	@Override
	public void onPlayerChangeWorld(PlayerChangeWorldListener listener) {
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(listener::onChangeWorld);
	}

	// ---- gameplay events -----------------------------------------------

	@Override
	public void onBlockBreak(BlockBreakListener listener) {
		PlayerBlockBreakEvents.AFTER.register(listener::onBlockBreak);
	}

	@Override
	public void onUseBlock(UseBlockListener listener) {
		UseBlockCallback.EVENT.register(listener::onUseBlock);
	}

	@Override
	public void onUseItem(UseItemListener listener) {
		UseItemCallback.EVENT.register(listener::onUseItem);
	}

	@Override
	public void onEntityKilledOther(EntityKillListener listener) {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(
				(world, entity, killedEntity) ->
						listener.onKilledOther(world, entity, killedEntity));
	}

	@Override
	public void onLivingDamage(LivingDamageListener listener) {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(listener::afterDamage);
	}

	@Override
	public void onCommandRegistration(CommandRegistrationListener listener) {
		CommandRegistrationCallback.EVENT.register(listener::register);
	}

	// ---- datapack reload -----------------------------------------------

	@Override
	public void registerReloadListener(ResourceLocation id, Consumer<ResourceManager> apply) {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
				new SimpleSynchronousResourceReloadListener() {
					@Override
					public ResourceLocation getFabricId() {
						return id;
					}

					@Override
					public void onResourceManagerReload(ResourceManager manager) {
						apply.accept(manager);
					}
				});
	}
}
