package com.dwurdy.lifepath.compat.railways;

import com.dwurdy.lifepath.LifepathMod;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Steam 'n' Rails possessed-conductor operator seam (NeoForge-only, gated by
 * {@code lifepath.compat.railways.mixins.json}'s {@code requiredMods=
 * ["railways"]} — railways hard-depends on create, so the create adapter is
 * always present when this runs).
 *
 * <p><b>The gap:</b> verified against railways 0.2.1 bytecode —
 * {@code ConductorEntity$RemoteControlGoal} drives trains through
 * {@code CarriageContraptionEntity.control(pos, controls, fakePlayer)}, so
 * {@code CONTROLLED_BY} holds the conductor's {@code FakePlayer} UUID — and
 * that UUID is SHARED: {@code ConductorEntity.FAKE_PLAYER_PROFILE} is one
 * static profile ({@code B0FADEE5-4411-3475-ADD0-C4EA7E30D050 "[Conductor]"})
 * for every conductor on the server. Unpossessed, that correctly resolves
 * "unattended" (FakePlayers are never in the player list). But railways also
 * lets a real player possess a conductor ({@code ConductorEntity.
 * startViewing(ServerPlayer)} → {@code ServerPlayerPossessionAccess.
 * railways$setPossessedConductor}, server-side) and drive the train through
 * it — the machine acts then belong to a real operator the create adapter
 * cannot see.
 *
 * <p><b>The seam:</b> {@code RailwaysCarriageControlMixin} calls
 * {@link #onCarriageControl} at HEAD of {@code control(...)} — the one funnel
 * a conductor's inputs always pass through while it drives. When the caller
 * is a conductor holder ({@code IConductorHoldingFakePlayer}), the possessing
 * {@code ServerPlayer} is found via {@code railways$getPossessedConductor}
 * and recorded per carriage entity. {@link #possessedOperator} is consulted
 * by {@code CreateAttendedAutomation.controlledPlayer} only after the normal
 * CONTROLLED_BY player-list lookup fails, and re-verifies the live possession
 * link on every call — a stale record (player unpossessed, or possessing a
 * different conductor now) resolves to {@code null} and drops back to
 * NON_PLAYER. Records are keyed by carriage-entity UUID, never by the shared
 * fake-player UUID, so two possessed conductors on different trains pay their
 * own drivers.
 *
 * <p>All foreign members are javap-verified public — resolved lazily with a
 * {@code publicLookup}, first failure cuts the bridge for the session exactly
 * like {@code CreateAttendedAutomation}. This class links no railways or
 * create types and loads safely without either mod.
 */
public final class RailwaysPossessedOperator {
	private RailwaysPossessedOperator() {
	}

	private static final String FAKE_HOLDER =
			"com.railwayteam.railways.content.conductor.IConductorHoldingFakePlayer";
	private static final String POSSESSION_ACCESS =
			"com.railwayteam.railways.content.conductor.ServerPlayerPossessionAccess";
	private static final String CONDUCTOR =
			"com.railwayteam.railways.content.conductor.ConductorEntity";

	private static volatile boolean resolved;
	private static volatile boolean cut;
	@Nullable private static Class<?> fakeHolderClass;
	@Nullable private static MethodHandle getConductor;           // fake player → ConductorEntity
	@Nullable private static MethodHandle possessedConductorGet;  // ServerPlayer → ConductorEntity

	/**
	 * carriage-entity UUID → record of who drives through this carriage's
	 * conductor. Keyed on the entity, not the fake player — the fake UUID is
	 * one shared profile.
	 */
	private record DriveThrough(UUID possessor, UUID conductor) {
	}

	private static final Map<UUID, DriveThrough> DRIVING = new ConcurrentHashMap<>();

	/**
	 * HEAD of {@code CarriageContraptionEntity.control(pos, controls, player)}
	 * — the conductor goal's every input call lands here. A conductor holder
	 * with a live possessor records the drive-through; anything else (a real
	 * player grabbing the controls, an unpossessed conductor) clears the
	 * record so the last-writer state mirrors CONTROLLED_BY.
	 */
	public static void onCarriageControl(Object carriageEntity, Player controller) {
		if (!(carriageEntity instanceof Entity entity) || !resolve()) {
			return;
		}
		try {
			if (controller == null || !fakeHolderClass.isInstance(controller)) {
				DRIVING.remove(entity.getUUID());
				return;
			}
			Object conductor = getConductor.invoke(controller);
			if (!(conductor instanceof Entity conductorEntity)) {
				DRIVING.remove(entity.getUUID());
				return;
			}
			MinecraftServer server = entity.getServer();
			if (server == null) {
				return;
			}
			for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
				if (possessedConductorGet.invoke(candidate) == conductor) {
					DRIVING.put(entity.getUUID(),
							new DriveThrough(candidate.getUUID(), conductorEntity.getUUID()));
					return;
				}
			}
			// Conductor drives on its own — golem labour is NON_PLAYER.
			DRIVING.remove(entity.getUUID());
		} catch (Throwable t) {
			cutOnce(t);
		}
	}

	/**
	 * The real player driving this carriage through a possessed conductor, or
	 * null. Consulted only when the entity's CONTROLLED_BY UUID is not an
	 * online player (the shared conductor fake resolves here). Re-verifies
	 * possession against the live ServerPlayer state every call, so the seam
	 * needs no eviction hook — a dead record can never overpay.
	 */
	@Nullable
	public static ServerPlayer possessedOperator(MinecraftServer server, Object entity) {
		if (!(entity instanceof Entity e) || !resolved || cut) {
			return null;
		}
		DriveThrough drive = DRIVING.get(e.getUUID());
		if (drive == null) {
			return null;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(drive.possessor());
		if (player == null || !player.isAlive()) {
			return null;
		}
		try {
			Object possessed = possessedConductorGet.invoke(player);
			return possessed instanceof Entity possessedEntity
					&& possessedEntity.getUUID().equals(drive.conductor())
							? player : null;
		} catch (Throwable t) {
			cutOnce(t);
			return null;
		}
	}

	/** One-shot resolution of the foreign members, mirroring the create bridge. */
	private static boolean resolve() {
		if (cut) {
			return false;
		}
		if (resolved) {
			return true;
		}
		synchronized (RailwaysPossessedOperator.class) {
			if (resolved || cut) {
				return !cut;
			}
			try {
				Class<?> holder = Class.forName(FAKE_HOLDER);
				Class<?> access = Class.forName(POSSESSION_ACCESS);
				Class<?> conductor = Class.forName(CONDUCTOR);
				MethodHandles.Lookup lk = MethodHandles.publicLookup();
				getConductor = lk.findVirtual(holder, "getConductor",
						MethodType.methodType(conductor));
				possessedConductorGet = lk.findVirtual(access,
						"railways$getPossessedConductor",
						MethodType.methodType(conductor));
				fakeHolderClass = holder;
				resolved = true;
				LifepathMod.LOGGER.info(
						"Railways possessed-conductor bridge active — trains "
								+ "driven through a possessed conductor pay the possessor");
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
					"Railways possessed-conductor bridge cut — {} ({}). "
							+ "Conductor-driven acts stay NON_PLAYER for the session.",
					t.getClass().getSimpleName(), t.getMessage());
		}
	}
}
