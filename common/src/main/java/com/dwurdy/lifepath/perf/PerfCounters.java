package com.dwurdy.lifepath.perf;

import com.dwurdy.lifepath.LifepathMod;
import com.dwurdy.lifepath.config.LifepathConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * M7-3 lightweight hot-path instrumentation. Each key tracks count, total
 * nanos and the worst single sample; a periodic summary is logged at INFO
 * while {@code debug_logging} is on. Overhead when quiet: one
 * {@link System#nanoTime} pair per measured call + LongAdder increments —
 * no allocation on the hot path.
 */
public final class PerfCounters {
	private static final class Stat {
		final LongAdder calls = new LongAdder();
		final LongAdder nanos = new LongAdder();
		final java.util.concurrent.atomic.AtomicLong max =
				new java.util.concurrent.atomic.AtomicLong();
	}

	private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();
	private static long lastReportMs;
	private static final long REPORT_INTERVAL_MS = 60_000;

	private PerfCounters() {
	}

	/** Records one sample. */
	public static void record(String key, long nanos) {
		Stat s = STATS.computeIfAbsent(key, k -> new Stat());
		s.calls.increment();
		s.nanos.add(nanos);
		s.max.accumulateAndGet(nanos, Math::max);
		maybeReport();
	}

	/** Times {@code r} and records under {@code key}. */
	public static void time(String key, Runnable r) {
		long t0 = System.nanoTime();
		try {
			r.run();
		} finally {
			record(key, System.nanoTime() - t0);
		}
	}

	/** Times {@code r}, returning its result. */
	public static <T> T time(String key, java.util.function.Supplier<T> r) {
		long t0 = System.nanoTime();
		try {
			return r.get();
		} finally {
			record(key, System.nanoTime() - t0);
		}
	}

	/** Emits the rolling summary when debug logging is enabled + interval due. */
	private static void maybeReport() {
		if (!LifepathConfig.getOrDefault(LifepathConfig.GENERAL,
				"debug_logging", false)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastReportMs < REPORT_INTERVAL_MS) {
			return;
		}
		lastReportMs = now;
		LifepathMod.LOGGER.info("[perf] {}", summary());
	}

	/** Human-readable rolling totals — also used by the report/doc. */
	public static String summary() {
		StringBuilder sb = new StringBuilder();
		STATS.entrySet().stream()
				.sorted(Map.Entry.<String, Stat>comparingByKey())
				.forEach(e -> {
					Stat s = e.getValue();
					long n = s.calls.sum();
					double avgUs = n == 0 ? 0 : s.nanos.sum() / 1000.0 / n;
					sb.append(e.getKey()).append(" calls=").append(n)
							.append(" avg=").append(String.format("%.1f", avgUs))
							.append("us max=")
							.append(String.format("%.1f", s.max.get() / 1000.0))
							.append("us; ");
				});
		return sb.length() == 0 ? "(no samples)" : sb.toString();
	}

	/** Test hook. */
	static void resetForTests() {
		STATS.clear();
		lastReportMs = 0;
	}
}
