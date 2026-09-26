package io.github.durdeuvlad.lifepath.event;

import io.github.durdeuvlad.lifepath.LifepathMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.util.Identifier;

/**
 * The activity bus (M2-3): producers {@link #publish} normalized events,
 * subscribers listen by activity type or to everything. The dispatcher knows
 * nothing about skills, XP, or external mods — it moves
 * {@link ActivityEvent}s from whoever saw the gameplay to whoever consumes it.
 *
 * <p>Server-side, server main thread. A listener throwing is isolated
 * (ERROR + the rest still run) — a broken consumer must not eat the event.
 */
public final class ActivityDispatcher {
	private ActivityDispatcher() {
	}

	@FunctionalInterface
	public interface Listener {
		void onActivity(ActivityEvent event);
	}

	private static final Map<Identifier, List<Listener>> BY_TYPE = new ConcurrentHashMap<>();
	private static final List<Listener> ANY = new CopyOnWriteArrayList<>();

	/** Subscribes to one activity type (e.g. {@code lifepath:mining}). */
	public static void register(Identifier type, Listener listener) {
		BY_TYPE.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
	}

	/** Subscribes to every activity event. */
	public static void registerAny(Listener listener) {
		ANY.add(listener);
	}

	/** Publishes one event to type subscribers then any-subscribers. Listener errors are isolated. */
	public static void publish(ActivityEvent event) {
		for (Listener l : BY_TYPE.getOrDefault(event.type(), List.of())) {
			invoke(l, event);
		}
		for (Listener l : ANY) {
			invoke(l, event);
		}
	}

	private static void invoke(Listener listener, ActivityEvent event) {
		try {
			listener.onActivity(event);
		} catch (Exception e) {
			LifepathMod.LOGGER.error("activity listener threw on {}", event.type(), e);
		}
	}

	/** Test hook: drops every subscription. Not for production use. */
	public static void resetForTests() {
		BY_TYPE.clear();
		ANY.clear();
	}
}
