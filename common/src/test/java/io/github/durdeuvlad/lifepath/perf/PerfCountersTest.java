package io.github.durdeuvlad.lifepath.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M7-3: the counters must be alloc-free, concurrency-safe accumulators —
 * a broken {@code max} or a shared-mutable summary would poison every
 * downstream budget decision.
 */
class PerfCountersTest {

	@BeforeEach
	void reset() {
		PerfCounters.resetForTests();
	}

	@Test
	void recordAccumulatesCallsAndNanos() {
		PerfCounters.record("k", 1_000);
		PerfCounters.record("k", 2_000);
		PerfCounters.record("k", 500);
		String s = PerfCounters.summary();
		assertTrue(s.contains("k calls=3"), s);
		// avg = 3500/3 ≈ 1.2us; max = 2.0us
		assertTrue(s.contains("max=2.0us"), s);
	}

	@Test
	void timeRecordsElapsedForRunnable() {
		PerfCounters.time("t1", () -> {});
		assertTrue(PerfCounters.summary().contains("t1 calls=1"));
	}

	@Test
	void timeSupplierReturnsValueAndRecords() {
		int v = PerfCounters.time("t2", () -> 42);
		assertEquals(42, v);
		assertTrue(PerfCounters.summary().contains("t2 calls=1"));
	}

	@Test
	void timeRecordsWhenBodyThrows() {
		try {
			PerfCounters.time("boom", () -> { throw new RuntimeException("x"); });
		} catch (RuntimeException ignored) {
		}
		assertTrue(PerfCounters.summary().contains("boom calls=1"));
	}

	@Test
	void keysAreIsolated() {
		PerfCounters.record("a", 100);
		PerfCounters.record("b", 200);
		PerfCounters.record("a", 300);
		String s = PerfCounters.summary();
		assertTrue(s.contains("a calls=2"), s);
		assertTrue(s.contains("b calls=1"), s);
	}

	@Test
	void emptySummaryIsStable() {
		assertEquals("(no samples)", PerfCounters.summary());
	}
}
