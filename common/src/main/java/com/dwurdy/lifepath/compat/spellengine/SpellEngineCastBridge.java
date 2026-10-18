package com.dwurdy.lifepath.compat.spellengine;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.event.ActivityDispatcher;
import com.dwurdy.lifepath.event.ActivityEvent;
import com.dwurdy.lifepath.event.ActivityTypes;
import com.dwurdy.lifepath.platform.Platform;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * SpellEngine {@code SPELL_CAST} → Lifepath {@code performance} activity,
 * scoped to Bards RPG. Every RPG-series caster (wizard, paladin, healer,
 * bard…) runs through SpellEngine, so unfiltered republishing would feed
 * the bard-only performance skill on any cast — only spell ids in the
 * {@code bards_rpg} namespace (song casts) are forwarded; the xp_source
 * still owns the weighting.
 *
 * <p>Same resilience contract as {@code VampirismFactions}: the foreign
 * classes are resolved lazily by name and held reflectively, the subscription
 * is a {@link Proxy} implementing the {@code SpellCastEvent} interface.
 * Absent mod → inert; a SpellEngine update that renames members flips
 * {@link #cut} once and logs — it can never crash a tick. Pays on RELEASE /
 * TRIGGER (cast goes off), never on CHANNEL (charge start).
 */
public final class SpellEngineCastBridge {
	private SpellEngineCastBridge() {
	}

	private static final String MOD_ID = "spell_engine";

	private static volatile boolean resolved;
	private static volatile boolean cut;

	/**
	 * Wire the subscription. Called once from Lifepath init; a no-op when
	 * SpellEngine is absent or the bridge has cut.
	 */
	public static void init() {
		if (resolved || !resolve()) {
			return;
		}
		try {
			Class<?> events = Class.forName("net.spell_engine.api.spell.event.SpellEvents");
			Class<?> castEvent =
					Class.forName("net.spell_engine.api.spell.event.SpellEvents$SpellCastEvent");
			Object spellCast = events.getField("SPELL_CAST").get(null);
			Object handler = Proxy.newProxyInstance(castEvent.getClassLoader(),
					new Class<?>[] { castEvent }, SpellEngineCastBridge::dispatch);
			spellCast.getClass().getMethod("register", Object.class)
					.invoke(spellCast, handler);
			resolved = true;
			LifepathMod.LOGGER.info(
					"SpellEngine cast bridge active — bards_rpg performance XP live");
		} catch (Throwable t) {
			cutOnce(t);
		}
	}

	private static Object dispatch(Object proxy, Method method, Object[] args) {
		if ("onSpellCast".equals(method.getName()) && args != null && args.length == 1) {
			onCast(args[0]);
		} else if ("toString".equals(method.getName())) {
			return "lifepath$spellEngineCastBridge";
		} else if ("hashCode".equals(method.getName())) {
			return System.identityHashCode(proxy);
		} else if ("equals".equals(method.getName())) {
			return proxy == args[0];
		}
		return null;
	}

	private static void onCast(Object args) {
		try {
			Object action = args.getClass().getMethod("action").invoke(args);
			String actionName = action instanceof Enum<?> e ? e.name() : null;
			if ("CHANNEL".equals(actionName)) {
				return;
			}
			Object caster = args.getClass().getMethod("caster").invoke(args);
			if (!(caster instanceof ServerPlayer player) || isAutomation(player)) {
				return;
			}
			Object holder = args.getClass().getMethod("spell").invoke(args);
			Object key = holder.getClass().getMethod("unwrapKey").invoke(holder);
			if (!(key instanceof Optional<?> opt) || opt.isEmpty()) {
				return;
			}
			Object location = opt.get().getClass().getMethod("location").invoke(opt.get());
			if (!(location instanceof ResourceLocation spellId)
					|| !"bards_rpg".equals(spellId.getNamespace())) {
				return;
			}
			ActivityDispatcher.publish(new ActivityEvent(player, ActivityTypes.PERFORMANCE,
					spellId, Set.of(), ActivityEvent.Cause.PLAYER,
					System.currentTimeMillis(),
					actionName == null ? Map.of() : Map.of("action", actionName)));
		} catch (Throwable t) {
			cutOnce(t);
		}
	}

	private static boolean isAutomation(ServerPlayer player) {
		try {
			return Platform.isInitialized() && Platform.get().isAutomation(player);
		} catch (Throwable t) {
			return true;
		}
	}

	private static boolean resolve() {
		if (cut) {
			return false;
		}
		if (!Platform.isInitialized() || !Platform.get().isModLoaded(MOD_ID)) {
			return false;
		}
		return true;
	}

	private static void cutOnce(Throwable t) {
		if (!cut) {
			cut = true;
			LifepathMod.LOGGER.warn(
					"SpellEngine cast bridge cut — {} ({}). performance XP stays "
							+ "quiet for the session.",
					t.getClass().getSimpleName(), t.getMessage());
		}
	}
}
