# Multiplayer correctness audit (M7-2)

Server thread discipline: every lifecycle hook and all `CharacterManager`
mutations run on the server main thread — there is no true parallelism to
race; the correctness surface is *session identity*, *snapshot isolation*,
and *persistence timing*.

## Scenario matrix

| # | Scenario | Verdict | Evidence |
|---|---|---|---|
| 1 | Simultaneous XP on same action | PASS (structural) | Server-thread-serialized dispatcher; per-UUID `PlayerCharacterData`; `XpSourceRouter` awards per `event.player()` — no shared mutable state. |
| 2 | Rapid reconnect mid-sync/decay | PASS after fix | Zombie-session owner tracking was already correct for cache/evict; **defect found & fixed**: stale disconnect cleared the *new* session's `DIRTY` flag → `SERVER_STOPPING` flush could skip it → lost mutations. `DIRTY.remove` now runs only inside the owner-matched eviction. |
| 3 | Death/respawn | PASS after fix | `copyOnDeath` attachment carries data; **defect found & fixed**: `AFTER_RESPAWN` re-synced but never rebound `CachedCharacter.owner` → post-respawn `saveCharacter` silently no-oped (dead entity ≠ owner): unflushed mutations never persisted, disconnect eviction skipped → cache leak. Owner now rebinds on respawn. |
| 4 | Dimension travel | PASS | `AFTER_PLAYER_CHANGE_WORLD → syncCharacter`; same entity instance ⇒ owner stays valid. |
| 5 | Restart mid-activity | PASS | Periodic dirty flush (`character.flush_interval_ticks`) + `SERVER_STOPPING` flush + disconnect flush; `fullyPopulatedDataRoundTrips` pins every persisted field (skills, cooldowns, resources, conditions, signatures). |
| 6 | Concurrent AoE abilities | PASS (structural) | Cooldowns live on `PlayerCharacterData` — per-player by construction; overlapping radii only re-apply status effects (refresh semantics). |
| 7 | Cooldown sync under reconnect | PASS | Cooldown map persists epoch-ms; `snapshotForSync` prunes expired + strips `schedule/` keys + clears the anti-exploit ledger (`snapshotForSync*` tests). Client merges via deltas between snapshots. |
| 8 | Concurrent species/spec selection | PASS (structural) | Admin commands act on per-UUID data; `syncCharacter` is per-player; `FeedbackService` diffs are per-player baselines. |

## Deferred-live cells

A real 2-client pass is still scheduled at milestone validation (no headless
multi-client harness exists in dev). The two cells whose evidence is
structural rather than observed: #1 (XP serialization under load) and #6
(AoE overlap on real entities). All other cells have direct code/test evidence.

## Test additions

- `snapshotForSyncDoesNotSeeLaterMutations` — deep-copy isolation pinned.
- `snapshotForSyncStripsScheduleKeysAndLedger` — server bookkeeping never
  reaches the wire.
