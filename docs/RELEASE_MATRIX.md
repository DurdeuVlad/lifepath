# M10-4 — Release-Criteria Test Matrix (1.0 stabilization)

Executed against `main` @ `ac9bc6e` (post M10-1/M10-2/M10-3). Evidence is
either **live** (dedicated-server boot logs quoted inline) or **suite**
(automated test coverage — the suite is 331 tests, 0 failures on this commit).
Cells that could not be exercised live in this environment are labelled
`PARTIAL` rather than claimed.

## The nine tests (TIMELINE §13)

| # | Test | Verdict | Evidence |
|---|---|---|---|
| 1 | Fresh-world full loop | **PARTIAL** | *Live*: brand-new `run/` world — server `Done (1.519s)`, all 14 content domains loaded, `content validation clean — no issues`. *Suite*: `VerticalSliceTest` drives select-species→spec→XP→ability-fire end to end. No live client session (headless env) — the in-world play loop is suite-covered, not live-verified. |
| 2 | Existing-world upgrade | **PASS (suite)** | `CharacterPersistenceTest` pushes a hand-built pre-v2 blob through the real `deserialize` → v1→v2 chain: data version stamped, valid fields preserved, legacy bare-string conditions converted, unknown refs dropped — exactly the M10-2 fixture. |
| 3 | Dedicated server | **PASS (live)** | Clean server install, `44 mods` load, `Done (1.375–1.519s)` on two boots, zero errors/warnings beyond the expected `overgeared not wired` note (absent optional mod — graceful). |
| 4 | Multiplayer soak | **PARTIAL** | *Live*: server sustained through the whole matrix (3 boots, reloads, probes) with no state repair. *Suite*: M7-2 seam tests cover the disconnect-dirty-flag race and respawn owner rebind — the two known desync-class bugs. No second client exists in this environment; a real multi-client soak remains a beta-program (M10.5-2) item. |
| 5 | Death/respawn | **PASS (suite)** | `CharacterPersistenceTest`/`CharacterManager` tests cover respawn owner rebinding — persisted state keeps saving after death/respawn (the M7-2 fix). Persistence is codec round-tripped on every save. |
| 6 | Dimension travel | **PASS (suite)** | Character state lives on a player-scoped attachment (entity, not dimension/world path); save/load codec tests are dimension-agnostic. Live: server boots with all dimensions and no per-dimension code paths exist. |
| 7 | `/lifepath reload` | **PASS (live)** | Console: `Lifepath reload finished: 15 reloadables, 0 failed` — all domains re-read including `engine_config` (config hot-reload), then `content validation clean — no issues`. Run twice across boots; identical results. |
| 8 | Datapack override precedence | **PASS (live)** | Probe pack (`run/world/datapacks/m104_probe`) overriding shipped `lifepath:human` — validation flagged the probe-only ref: `ERROR lifepath:human ref->resource: references missing resource 'lifepath:m104_probe_resource'` at both boot and `/lifepath reload`. Pack files provably take precedence over jar-shipped content. |
| 9 | Corrupted/missing data recovery | **PASS** | *Live* (dangling content): the probe's missing resource id produced a named validation ERROR while the server continued to `Done` and clean-stopped — warn, keep running, never crash on content. *Suite* (corrupt save): `CharacterPersistenceTest` decodes a deliberately malformed character record → writes a corrupt-save backup, logs, falls back to defaults — verified end-to-end. |

## The seven release criteria

| Criterion | Verdict | Evidence |
|---|---|---|
| No known progression duplication exploits | **PASS** | Diminishing-returns window (`diminishing.toml`, 4-tier taper), placed-block re-mine tracking, `player_caused_only` gating, `unmapped_sources_award_xp` whitelist switch. Two milestone bug-hunts (M7, M9) found and fixed the only discovered exploits; M9 hunt clean otherwise. |
| No known save corruption | **PASS** | `CharacterPersistence` backup-on-decode-failure + defaults fallback (suite-verified); session-lock and flush semantics live-verified across 3 boots; `data_version` chain tested v0→v2 (`MIGRATIONS.md`). |
| No client/server desync in core systems | **PARTIAL** | All character state is server-owned with S2C sync snapshots (`lifepath:character/sync`); M7-2 fixed the two real desync-class bugs (disconnect race, respawn rebind) with tests. No live two-client soak in this environment — deferred to M10.5-2 beta. |
| No mandatory Origins/Apoli/KubeJS dep | **PASS** | Zero compile/runtime references to Origins, Origins Classes, Apoli, OriginJS, or KubeJS in `build.gradle`/`fabric.mod.json`/src. All validation ran with them absent (`docs/COMPAT.md`). |
| Core configuration documented | **PASS** | `docs/CONFIGURATION.md` — all 9 spec files, every key with default + validator range + effect + reload semantics, generated from source (M10-3). |
| Data-driven extension path documented | **PASS** | `docs/DATAPACK_API.md` — every domain schema + the Mythril Ore → Mining XP worked example; the override probe above additionally proves pack precedence live (M10-3). |
| Server runs continuously without manual state repair | **PASS** | Three consecutive dedicated-server boots across the matrix: clean `Done`, clean validation, clean `stop` — including a boot *while* a world pack injected a dangling reference (warn-and-continue, no repair needed). |

## Follow-up issues filed

None — the matrix surfaced no new defects. The two `PARTIAL` cells (live
fresh-world play loop, live multi-client soak) are environmental limits, not
code failures; both are already the explicit scope of **M10.5-1/2** (public
beta + real-player validation), which is where live-client evidence is meant
to come from.
