package io.github.durdeuvlad.lifepath.testsupport;

import io.github.durdeuvlad.lifepath.platform.PlatformServices;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Test stub for the platform seam (M13-3): headless semantics for everything
 * common tests can reach — persistence is a real in-memory map so
 * character-data round-trips work; registrations record for duplicate checks;
 * event listeners are accepted and never fired (tests drive behavior
 * directly, as they did under the old fabric-loader test classpath).
 */
public final class NoopPlatformServices implements PlatformServices {

	private final Map<UUID, CompoundTag> characterData = new ConcurrentHashMap<>();
	private final Set<CustomPacketPayload.Type<?>> s2cTypes = new CopyOnWriteArraySet<>();
	private final Set<CustomPacketPayload.Type<?>> c2sTypes = new CopyOnWriteArraySet<>();
	private final Set<CustomPacketPayload.Type<?>> c2sReceivers = new CopyOnWriteArraySet<>();
	private final List<Consumer<MinecraftServer>> endTickListeners = new ArrayList<>();
	private final Map<ResourceLocation, Consumer<ResourceManager>> reloadListeners =
			new java.util.LinkedHashMap<>();

	@Override
	public boolean isModLoaded(String modId) {
		return false;
	}

	@Override
	public boolean isAutomation(net.minecraft.world.entity.player.Player player) {
		return false;
	}

	@Override
	public Path configDir() {
		return Path.of(System.getProperty("java.io.tmpdir"), "lifepath-test");
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return Optional.empty();
	}

	@Override
	public <T> List<T> entrypoints(String key, Class<T> type) {
		return List.of();
	}

	@Override
	public void registerCharacterAttachment() {
	}

	@Override
	@Nullable
	public CompoundTag getCharacterData(ServerPlayer player) {
		return characterData.get(player.getUUID());
	}

	@Override
	public void setCharacterData(ServerPlayer player, CompoundTag data) {
		characterData.put(player.getUUID(), data);
	}

	@Override
	public <T extends CustomPacketPayload> void registerS2C(
			CustomPacketPayload.Type<T> id,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		if (!s2cTypes.add(id)) {
			throw new IllegalStateException("duplicate S2C type " + id.id());
		}
	}

	@Override
	public <T extends CustomPacketPayload> void registerC2S(
			CustomPacketPayload.Type<T> id,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		if (!c2sTypes.add(id)) {
			throw new IllegalStateException("duplicate C2S type " + id.id());
		}
	}

	@Override
	public <T extends CustomPacketPayload> void onC2S(
			CustomPacketPayload.Type<T> id, BiConsumer<T, ServerPlayer> handler) {
		if (!c2sReceivers.add(id)) {
			throw new IllegalStateException("duplicate C2S receiver for payload " + id.id());
		}
	}

	@Override
	public void sendTo(ServerPlayer player, CustomPacketPayload payload) {
	}

	@Override
	public void onEndServerTick(Consumer<MinecraftServer> listener) {
		endTickListeners.add(listener);
	}

	@Override
	public void onServerStopping(Consumer<MinecraftServer> listener) {
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
	}

	@Override
	public void onPlayerJoin(Consumer<ServerPlayer> listener) {
	}

	@Override
	public void onPlayerDisconnect(Consumer<ServerPlayer> listener) {
	}

	@Override
	public void onPlayerRespawn(PlayerRespawnListener listener) {
	}

	@Override
	public void onPlayerChangeWorld(PlayerChangeWorldListener listener) {
	}

	@Override
	public void onBlockBreak(BlockBreakListener listener) {
	}

	@Override
	public void onUseBlock(UseBlockListener listener) {
	}

	@Override
	public void onUseItem(UseItemListener listener) {
	}

	@Override
	public void onEntityKilledOther(EntityKillListener listener) {
	}

	@Override
	public void onLivingDamage(LivingDamageListener listener) {
	}

	@Override
	public void onCommandRegistration(CommandRegistrationListener listener) {
	}

	@Override
	public void registerReloadListener(ResourceLocation id,
			Consumer<ResourceManager> apply) {
		if (reloadListeners.putIfAbsent(id, apply) != null) {
			throw new IllegalStateException("duplicate reload listener " + id);
		}
	}

	/** Test hook: drives one end-tick on a mock/real server. */
	public void fireEndTick(MinecraftServer server) {
		for (var l : endTickListeners) {
			l.accept(server);
		}
	}
}
