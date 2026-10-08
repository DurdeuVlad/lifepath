package com.dwurdy.lifepath.compat.sereneseasons;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.platform.Platform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * Soft-dep read bridge into Serene Seasons (compat A6): resolves the
 * world's current season for the {@code lifepath:season} condition. Same
 * resilience contract as {@code VampirismFactions} — the foreign classes
 * resolve lazily by name on first use; anything missing or mis-signed
 * flips {@link #cut}, after which every query is a constant false.
 *
 * <p>The match vocabulary covers three granularities: the coarse season
 * ({@code spring|summer|autumn|winter}), the sub-season
 * ({@code early_spring|…|late_winter}), and — only where the biome at the
 * checked position runs tropical seasons — the tropical pair
 * ({@code wet|dry} plus {@code early_wet|…|late_dry}). A datapack
 * {@code seasons:[winter]} therefore fires across all three winter
 * sub-seasons, while {@code seasons:[early_winter]} pins the first third.
 */
public final class SereneSeasonsBridge {
	private SereneSeasonsBridge() {
	}

	private static final String MOD_ID = "sereneseasons";
	private static final String HELPER = "sereneseasons.api.season.SeasonHelper";
	private static final String STATE = "sereneseasons.api.season.ISeasonState";
	private static final String SUB = "sereneseasons.api.season.Season$SubSeason";
	private static final String TROPICAL = "sereneseasons.api.season.Season$TropicalSeason";

	private static volatile boolean resolved;
	private static volatile boolean cut;
	private static volatile boolean warnedAbsent;
	@Nullable private static MethodHandle getSeasonState;
	@Nullable private static MethodHandle getSeason;        // ISeasonState → Season
	@Nullable private static MethodHandle getSubSeason;     // ISeasonState → SubSeason
	@Nullable private static MethodHandle getTropical;      // ISeasonState → TropicalSeason
	@Nullable private static MethodHandle usesTropical;     // SeasonHelper(Holder<Biome>) → boolean
	@Nullable private static MethodHandle subName;          // SubSeason → String
	@Nullable private static MethodHandle enumName;         // Enum → String

	/**
	 * True when the season at {@code pos} in {@code level} matches any name
	 * in {@code wanted} (lowercase). False when the mod is absent or the
	 * bridge has cut — callers fail closed.
	 */
	public static boolean matches(Level level, BlockPos pos, Set<String> wanted) {
		if (!resolve()) {
			warnAbsentOnce();
			return false;
		}
		try {
			Object state = getSeasonState.invoke(level);
			if (state == null) {
				return false;
			}
			Holder<Biome> biome = level.getBiome(pos);
			if ((boolean) usesTropical.invoke(biome)) {
				Object tropical = getTropical.invoke(state);
				if (tropical == null) {
					return false;
				}
				String t = ((String) enumName.invoke(tropical))
						.toLowerCase(Locale.ROOT);
				// Coarse tropical: early/mid/late all roll up to wet/dry.
				return wanted.contains(t)
						|| (t.endsWith("_wet") && wanted.contains("wet"))
						|| (t.endsWith("_dry") && wanted.contains("dry"));
			}
			Object sub = getSubSeason.invoke(state);
			if (sub != null && wanted.contains(
					((String) subName.invoke(sub)).toLowerCase(Locale.ROOT))) {
				return true;
			}
			Object season = getSeason.invoke(state);
			return season != null && wanted.contains(
					((String) enumName.invoke(season)).toLowerCase(Locale.ROOT));
		} catch (Throwable t) {
			cutOnce(t);
			return false;
		}
	}

	/** One-shot lazy resolution — foreign classes never load while absent. */
	@SuppressWarnings("unchecked")
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
		synchronized (SereneSeasonsBridge.class) {
			if (resolved || cut) {
				return !cut;
			}
			try {
				Class<?> helper = Class.forName(HELPER);
				Class<?> state = Class.forName(STATE);
				Class<?> sub = Class.forName(SUB);
				Class<?> tropical = Class.forName(TROPICAL);
				MethodHandles.Lookup lk = MethodHandles.publicLookup();
				getSeasonState = lk.findStatic(helper, "getSeasonState",
						MethodType.methodType(state, Level.class));
				getSeason = lk.findVirtual(state, "getSeason",
						MethodType.methodType(Class.forName(
								"sereneseasons.api.season.Season")));
				getSubSeason = lk.findVirtual(state, "getSubSeason",
						MethodType.methodType(sub));
				getTropical = lk.findVirtual(state, "getTropicalSeason",
						MethodType.methodType(tropical));
				usesTropical = lk.findStatic(helper, "usesTropicalSeasons",
						MethodType.methodType(boolean.class, Holder.class));
				subName = lk.findVirtual(sub, "getSerializedName",
						MethodType.methodType(String.class));
				enumName = lk.findVirtual(Enum.class, "name",
						MethodType.methodType(String.class));
				resolved = true;
				LifepathMod.LOGGER.info(
						"Serene Seasons bridge active — lifepath:season live");
				return true;
			} catch (Throwable t) {
				cutOnce(t);
				return false;
			}
		}
	}

	private static void warnAbsentOnce() {
		if (!warnedAbsent) {
			warnedAbsent = true;
			LifepathMod.LOGGER.warn(
					"lifepath:season condition evaluated but Serene Seasons "
							+ "is absent — all season checks return false");
		}
	}

	private static void cutOnce(Throwable t) {
		if (!cut) {
			cut = true;
			LifepathMod.LOGGER.warn(
					"Serene Seasons bridge cut — {} ({}). lifepath:season is "
							+ "false for the session.",
					t.getClass().getSimpleName(), t.getMessage());
		}
	}
}
