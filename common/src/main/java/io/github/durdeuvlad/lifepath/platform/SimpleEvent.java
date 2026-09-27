package io.github.durdeuvlad.lifepath.platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Minimal internal event dispatcher — replaces Fabric's {@code EventFactory}
 * for Lifepath's own domain events (M13-3). Same {@code register}/{@code
 * invoker} call shape; listeners run in registration order. Backed by
 * {@link CopyOnWriteArrayList} so a listener may (un)register during dispatch
 * without {@code ConcurrentModificationException}.
 */
public final class SimpleEvent<T> {
	private final List<T> listeners = new CopyOnWriteArrayList<>();
	private final T invoker;

	/** {@code combiner} folds the listener list into the invoker called by {@link #invoker()}. */
	public SimpleEvent(Function<List<T>, T> combiner) {
		this.invoker = combiner.apply(listeners);
	}

	public void register(T listener) {
		listeners.add(listener);
	}

	public T invoker() {
		return invoker;
	}
}
