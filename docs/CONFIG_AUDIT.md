# Balance-config coverage audit (M7-4)

Every high-risk tunable from TIMELINE §10 is operator-editable without
recompiling. Config files live under `config/lifepath/` (one TOML per domain);
`/lifepath reload` reloads data files — config values read through
`LifepathConfig.getOrDefault` apply on next access (per-tick keys are live,
cached values note it below).

## Audit table

| Tunable | Key | File | Default |
|---|---|---|---|
| Debug logging toggle | `debug_logging` | `general.toml` | `false` |
| Dirty-state flush cadence | `flush_interval_ticks` | `character.toml` | `6000` (5 min) |
| Rank band thresholds (7) | `band_untrained`…`band_legendary` | `skills.toml` | `0/1/20/40/60/80/95` |
| Global XP rate | `global_xp_multiplier` | `skills.toml` | `1.0` |
| Per-skill XP rates (6) | `<skill>_xp_multiplier` | `skills.toml` | `1.0` each |
| Aptitude XP multipliers (D–S) | `aptitude_<g>_xp_multiplier` | `skills.toml` | `0.70/0.90/1.00/1.25/1.50` |
| Aptitude decay multipliers (D–S) | `aptitude_<g>_decay_multiplier` | `skills.toml` | `1.25/1.10/1.00/0.80/0.60` |
| Unknown-source XP policy | `unmapped_sources_award_xp` | `skills.toml` | `true` |
| Decay master toggle | `enabled` | `decay.toml` | `true` |
| Decay grace window | `grace_hours` | `decay.toml` | `48.0` |
| Decay maintenance cadence | `maintenance_minutes` | `decay.toml` | `60.0` |
| Decay band bounds ×5 | `band_<n>_upper` | `decay.toml` | `25/50/75/90/100` |
| Decay band rates ×5 | `band_<n>_rate` | `decay.toml` | `0/0.05/0.10/0.20/0.35` lv/day |
| Ability system kill switch | `enabled` | `abilities.toml` | `true` |
| Passive eval interval | `passive_interval_ticks` | `abilities.toml` | `20` (1 s) |
| Nearby-scan radius cap | `nearby_max_radius` | `abilities.toml` | `32` |
| Global cooldown scalar | `cooldown_multiplier` | `abilities.toml` | `1.0` |
| Cooldown persistence floor | `persist_min_seconds` | `abilities.toml` | `5.0` |
| Resource sweep interval | `tick_interval_ticks` | `resources.toml` | `20` (1 s) |
| Diminishing-returns toggle | `enabled` | `diminishing.toml` | `true` |
| DR rolling window | `window_hours` | `diminishing.toml` | `4.0` |
| DR tier thresholds | `tier_1_count`, `tier_2_count` | `diminishing.toml` | `64`, `256` |
| DR tier multipliers | `tier_<n>_multiplier` | `diminishing.toml` | `1.0/0.5/0.1` |
| Signature ledger cap (anti-exploit) | `signature_cap` | `diminishing.toml` | `4096` |
| HUD toggle/position/scale | `hud_*` | `client.toml` | `true`/`top_left`/`1.0` |

Per-ability tunables (damage multipliers, cooldown seconds, radii, costs,
intervals, resource gains/drains) are DATA, not config — they live in
`data/*/ability/*.json` and reload via `/lifepath reload`. That is the
intended seam: config = global balance scalars, data = content values.

## Hardcoded exceptions (bounds, not balance)

| Value | Location | Why hardcoded |
|---|---|---|
| `MIN_FLUSH_INTERVAL_TICKS = 200` | `CharacterManager` | Safety floor under `flush_interval_ticks` — prevents a config value from turning the flush loop per-tick. |
| Signature persistence bound `4096` | `PlayerCharacterData` | NBT decode bound guarding corrupt saves; `signature_cap` validator is clamped to it. |
| `blocks_in_radius` caps (radius 32, limit 512) | `BuiltinTargets` | Hard bounds on a full-cube scan — these guard the tick, not balance. |

## Debug tooling (M7-4)

- `/lifepath debug character <player>` — full state + XP modifier pipeline.
- `/lifepath debug ability <player> <ability>` — ownership, per-condition live
  eval, cooldown state.
- `/lifepath debug skill <player> <skill>` — level/xp/floor/aptitude,
  last-use, decay projection, in-window diminishing-returns counts.
- `/lifepath cooldown clear <player> [ability]` — per-ability or global clear;
  logged as an admin action.

All report EFFECTIVE values (post-modifier, post-decay). Passive-schedule
markers are never shown or cleared — they are engine bookkeeping, not
player-visible cooldowns.
