# Performance budgets (M7-3)

Lifepath is a server-tick mod; the budget unit is **milliseconds of main-thread
time per invocation**, because every hot path runs on the server tick loop
(50 ms/tick). Budgets are sized so a realistic load (5 players, full passive
kit, decay bursts) stays under **1% of the tick** — 500 µs.

## Measured baseline (M7-3 harness)

Measured by `PerfBenchmarkTest` / `PerfSyncBenchmarkTest` (synthetic mid-game
characters: 8 skills, 12 cooldowns, 20 signature ledgers, 3 resources; JUnit,
dev machine — treat as relative cost, not absolute hardware truth):

| Hot path | Measured | Budget | Headroom |
|---|---|---|---|
| `ability.passive_sweep` (per player, 12 passives) | 25.5 µs | 200 µs/player | ~8× |
| — per ability-eval | 2.13 µs | 20 µs | ~9× |
| `decay.login_batch` (per player, 30d offline, 8 skills) | 11.5 µs | 500 µs/player | ~43× |
| `decay.maintenance_batch` (same shape) | 11.5 µs | 500 µs/player | ~43× |
| `sync.character` snapshot+encode | 315 µs | 1 000 µs | ~3× |
| — `sync.payload_bytes` | ~8.2 KB | 64 KB | ~8× |
| `scan.block_nearby` / `scan.entity_nearby` / `scan.*_in_radius` | see note | 5 000 µs/call | — |
| `resource.sweep` (per player) | not separately measured — see note | 200 µs/player | — |

World-scan paths (`*_nearby`, `*_in_radius`) depend on live `World` access and
cannot be measured headless; they are instrumented (`PerfCounters` emits a
60-second summary under `debug_logging`) and bounded by `nearby_max_radius`
(config, ≤64) and the `blocks_in_radius` 512-hit limit. The budget for a
radius-32 full cube scan (~270k `getBlockState` calls) is 5 ms/call — flag a
hotspot if the live summary approaches it.

`resource.sweep` shares the per-player tick loop and is instrumented; the same
200 µs/player budget applies.

## Configuration knobs (verified M7-3)

| Knob | File | Default | Bounds |
|---|---|---|---|
| `abilities.toml` `passive_interval_ticks` | engine tick gate | 20 (1 s) | 1–1200 |
| `abilities.toml` `nearby_max_radius` | scan radius cap | 32 | 1–64 |
| `resources.toml` `tick_interval_ticks` | resource sweep | 20 (1 s) | 1–1200 |
| `decay.toml` `maintenance_minutes` | maintenance decay pass | 60 | 0–14400 |
| `character.toml` `flush_interval_ticks` | dirty-data flush | ≥200 | — |

Per-ability `trigger.interval_ticks` adds a second gate inside the sweep
(schedule markers in the cooldown map), so data authors can slow individual
passives without touching the engine cadence.

## Structural guarantees verified

- **No per-tick decay**: decay runs (a) once lazily on JOIN against persisted
  timestamps, and (b) in a maintenance pass gated by `maintenance_minutes`
  (default 1 h). No END_SERVER_TICK decay work outside the gated interval.
- **No per-tick scans**: every `*_nearby` / `*_in_radius` evaluator runs only
  inside ability evaluation, which is gated by `passive_interval_ticks`
  AND the per-ability `interval_ticks` schedule marker.
- **Sync is bounded**: `syncCharacter` sends 3 payloads (character snapshot,
  identity, skills) + at most N resource deltas; the snapshot deep-copies via
  codec round-trip and prunes expired cooldowns, schedule markers, and the
  anti-exploit ledger before encode. Measured ~8 KB / ~315 µs for a mid-game
  character.

## Runtime telemetry

`PerfCounters` records calls/total-nanos/worst-sample per key with no
allocation on the hot path (`nanoTime` pair + `LongAdder`). A rolling summary
logs at INFO every 60 s while `general.toml` `debug_logging` is on:

```
[perf] ability.passive_sweep calls=… avg=25.5us max=…us; sync.payload_bytes …
```

To profile a live server: set `debug_logging = true` in `general.toml`, run
a session, read the `[perf]` lines. Follow-up issue-worthy if any avg
approaches its budget.

## Deferred

- Live-world scan cost (`getBlockState`/`getOtherEntities` under real chunk
  data) — instrumentation is in place; needs a multiplayer session.
- `ResourceUpdatePayload`/`FeedbackPayload` wire size — fixed-size codec
  records, negligible; instrumented count already exists via `sync.resource_update`.
