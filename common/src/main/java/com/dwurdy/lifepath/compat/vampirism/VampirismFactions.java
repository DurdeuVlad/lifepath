package com.dwurdy.lifepath.compat.vampirism;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.Platform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only probe into TeamLapen faction state (B6). One bridge covers both
 * Vampirism and Werewolves — Werewolves registers its faction into
 * Vampirism's {@code FactionPlayerHandler} registry, so
 * {@code FactionPlayerHandler.get(player).getCurrentFaction().getID()}
 * answers "is this player a vampire / werewolf" for either mod.
 *
 * <p>Resilience contract (the issue's auto-cut criterion): the class is
 * resolved lazily by name on first probe. Anything missing, throwing, or
 * lying about its signature flips {@link #cut} — from then on every call is
 * a constant false/empty and the failure logs once. A Vampirism update that
 * renames members degrades the feature; it can never crash a tick.
 */
public final class VampirismFactions {
	private VampirismFactions() {
	}

	private static final String MOD_ID = "vampirism";
	private static final String HANDLER =
			"de.teamlapen.vampirism.entity.factions.FactionPlayerHandler";

	private static volatile boolean resolved;
	private static volatile boolean cut;
	@Nullable private static MethodHandle getHandler;
	@Nullable private static MethodHandle getFaction;
	@Nullable private static MethodHandle getFactionId;
	@Nullable private static MethodHandle getLevel;

	/**
	 * The player's faction id ({@code "vampirism:vampire"},
	 * {@code "werewolves:werewolf"}, …) or empty when the player holds no
	 * faction, the mod is absent, or the bridge has cut.
	 */
	public static Optional<ResourceLocation> factionId(Player player) {
		Object faction = faction(player);
		if (faction == null) {
			return Optional.empty();
		}
		try {
			Object id = getFactionId.invoke(faction);
			return id instanceof ResourceLocation rl ? Optional.of(rl) : Optional.empty();
		} catch (Throwable t) {
			cutOnce(t);
			return Optional.empty();
		}
	}

	/** Current faction level — 0 when none/unavailable. */
	public static int factionLevel(Player player) {
		Object handler = handler(player);
		if (handler == null) {
			return 0;
		}
		try {
			Object lvl = getLevel.invoke(handler);
			return lvl instanceof Integer i ? i : 0;
		} catch (Throwable t) {
			cutOnce(t);
			return 0;
		}
	}

	@Nullable
	private static Object faction(Player player) {
		Object handler = handler(player);
		if (handler == null) {
			return null;
		}
		try {
			return getFaction.invoke(handler);
		} catch (Throwable t) {
			cutOnce(t);
			return null;
		}
	}

	@Nullable
	private static Object handler(Player player) {
		if (!resolve()) {
			return null;
		}
		try {
			return getHandler.invoke(player);
		} catch (Throwable t) {
			cutOnce(t);
			return null;
		}
	}

	/**
	 * One-shot lazy resolution — the class never loads while Vampirism is
	 * absent, so this probe is inert (and cheap) in vanilla packs.
	 */
	private static boolean resolve() {
		if (cut) {
			return false;
		}
		if (!Platform.get().isModLoaded(MOD_ID)) {
			return false;
		}
		if (resolved) {
			return true;
		}
		synchronized (VampirismFactions.class) {
			if (resolved || cut) {
				return !cut;
			}
			try {
				Class<?> handlerClass = Class.forName(HANDLER);
				MethodHandles.Lookup lk = MethodHandles.publicLookup();
				getHandler = lk.findStatic(handlerClass, "get",
						MethodType.methodType(handlerClass, Player.class));
				getFaction = lk.findVirtual(handlerClass, "getCurrentFaction",
						MethodType.methodType(
								Class.forName(
										"de.teamlapen.vampirism.api.entity.factions.IPlayableFaction")));
				getFactionId = lk.findVirtual(
						Class.forName(
								"de.teamlapen.vampirism.api.entity.factions.IFaction"),
						"getID", MethodType.methodType(ResourceLocation.class));
				getLevel = lk.findVirtual(handlerClass, "getCurrentLevel",
						MethodType.methodType(int.class));
				resolved = true;
				LifepathMod.LOGGER.info(
						"Vampirism faction bridge active — player_faction condition live");
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
					"Vampirism faction bridge cut — {} ({}). player_faction "
							+ "conditions stay false for the session.",
					t.getClass().getSimpleName(), t.getMessage());
		}
	}
}
