# Lifepath Configuration (M10-3)

Every config file lives under `config/lifepath/<name>.toml`. Each file is a
registered spec: **unknown keys warn and are ignored**, missing keys take
their default, and a file that fails to parse is renamed to
`<name>.toml.invalid` while defaults apply — a broken config never crashes
or silently half-loads. A `.toml` file with no registered spec name warns
(usually a typo'd filename).

**Reload**: `/lifepath reload` re-reads all config files live; data files are
re-read by datapack reload (`/reload` or `/lifepath reload`). Restart is
never required for a documented key.

`default` below is the shipped value; `range` is the validator bound — values
outside it are rejected and the default applies.

---

## `general.toml`

| Key | Default | Range | Effect |
|---|---|---|---|
| `debug_logging` | `false` | bool | Verbose dev logging. `LIFEPATH_DEBUG` env var also forces it on. |

## `character.toml`

| Key | Default | Range | Effect |
|---|---|---|---|
| `flush_interval_ticks` | `6000` | `≥ 200` | Ticks between periodic flushes of dirty character data to the persistent attachment. Saves also run on disconnect and server stop — this only bounds the crash-loss window. |

## `skills.toml`

| Key | Default | Range | Effect |
|---|---|---|---|
| `band_untrained` | `0` | `= 0` | Rank-band floor for "Untrained" (fixed — the level-0 band). |
| `band_novice` | `1` | `0 < v ≤ 100` | Level where "Novice" starts. |
| `band_apprentice` | `20` | `0 < v ≤ 100` | "Apprentice" starts. |
| `band_skilled` | `40` | `0 < v ≤ 100` | "Skilled" starts. |
| `band_expert` | `60` | `0 < v ≤ 100` | "Expert" starts. |
| `band_master` | `80` | `0 < v ≤ 100` | "Master" starts. |
| `band_legendary` | `95` | `0 < v ≤ 100` | "Legendary" starts. |
| `global_xp_multiplier` | `1.0` | `0 ≤ v ≤ 1000` | Multiplies all skill XP awards (the `lifepath:global_multiplier` modifier). |
| `mining/farming/smithing/fishing/foraging/engineering_xp_multiplier` | `1.0` | `0 ≤ v ≤ 1000` | Per-skill XP multiplier — shipped keys cover these six original skills only; newer skills are governed by `global_xp_multiplier` and aptitude/species modifiers. |
| `aptitude_{d,c,b,a,s}_xp_multiplier` | `0.70 / 0.90 / 1.00 / 1.25 / 1.50` | `0 ≤ v ≤ 100` | XP multiplier per aptitude grade. |
| `aptitude_{d,c,b,a,s}_decay_multiplier` | `1.25 / 1.10 / 1.00 / 0.80 / 0.60` | `0 ≤ v ≤ 100` | Decay-rate multiplier per aptitude grade (lower grades decay faster). |
| `unmapped_sources_award_xp` | `true` | bool | When `false`, `xp_source` rules only pay out on a specific `per_subject`/`per_tag` hit — the `base_xp` fallback is suppressed (strict whitelist). |

## `decay.toml` — skill decay ladder (SkillDecayService)

Decay applies per skill once `grace_hours` of real time pass since its last
meaningful use; bands slice progress by level (`band_N_upper` = top of the
band, `band_N_rate` = levels lost per real day while inside it).

| Key | Default | Range | Effect |
|---|---|---|---|
| `enabled` | `true` | bool | Master switch for decay (GAMEDESIGN §9). |
| `grace_hours` | `48.0` | `0 – 87600` | Real hours after last meaningful use before decay starts. |
| `maintenance_minutes` | `60.0` | `0 – 14400` | Minutes between the low-frequency decay maintenance pass. |
| `band_1_upper` / `band_1_rate` | `25` / `0.0` | `0 – 100` | Levels 0–25: no decay. |
| `band_2_upper` / `band_2_rate` | `50` / `0.05` | `0 – 100` | Levels 26–50 decay rate/day. |
| `band_3_upper` / `band_3_rate` | `75` / `0.10` | `0 – 100` | Levels 51–75. |
| `band_4_upper` / `band_4_rate` | `90` / `0.20` | `0 – 100` | Levels 76–90. |
| `band_5_upper` / `band_5_rate` | `100` / `0.35` | `0 – 100` | Levels 91–100. |

## `abilities.toml`

| Key | Default | Range | Effect |
|---|---|---|---|
| `enabled` | `true` | bool | Master switch for the ability system: passive sweeps, actives, damage modifiers. |
| `passive_interval_ticks` | `20` | `1 – 1200` | Base cadence of the passive-ability sweep. |
| `nearby_max_radius` | `32` | `1 – 64` | Hard cap on `entities_in_radius`/`blocks_in_radius`/`entity_nearby` params (bigger values clamp). |
| `cooldown_multiplier` | `1.0` | `0 – 100` | Scales every ability cooldown. |
| `persist_min_seconds` | `5.0` | `0 – 3600` | Cooldowns with ≤ this remaining at shutdown don't persist to the save. |

## `resources.toml`

| Key | Default | Range | Effect |
|---|---|---|---|
| `tick_interval_ticks` | `20` | `1 – 1200` | Cadence of the resource regen/band sweep. |

## `diminishing.toml` — diminishing returns (DiminishingReturns)

Rolling-window anti-grind: identical action signatures (per `type|sourceId`)
within `window_hours` get progressively less XP.

| Key | Default | Range | Effect |
|---|---|---|---|
| `enabled` | `true` | bool | Master switch (GAMEDESIGN §11.1). |
| `window_hours` | `4.0` | `0.0167 – 720` | Rolling real-time window counting identical signatures; older entries age out. |
| `tier_1_count` / `tier_1_multiplier` | `64` / `1.0` | count `1 – 4096`, mult `0 – 10` | Counts ≤ tier_1 get full XP. |
| `tier_2_count` / `tier_2_multiplier` | `256` / `0.5` | same | Counts between the tiers get half. |
| `tier_3_multiplier` | `0.1` | `0 – 10` | Counts above tier_2_count get 10%. |
| `signature_cap` | `4096` | `64 – 4096` | Max timestamps kept per signature (persistence bound). |

## `encumbrance.toml` — inventory load (M9-3)

| Key | Default | Range | Effect |
|---|---|---|---|
| `enabled` | `true` | bool | Master switch; off = no load bookkeeping or penalties. |
| `capacity` | `200.0` | `0 < v ≤ 1e6` | Base carry capacity before species/specialization multipliers. |
| `default_item_weight` | `1.0` | `0 – 1e4` | Weight of items with no `item_weight` entry. |
| `scan_interval_ticks` | `40` | `10 – 1200` | Inventory recount cadence (batched, not per-tick). |

## `client.toml` — client-side only

| Key | Default | Range | Effect |
|---|---|---|---|
| `hud_enabled` | `true` | bool | Show the Lifepath HUD element. |
| `hud_position` | `top_left` | `top_left`, `top_right`, `bottom_left`, `bottom_right` | HUD anchor corner. |
| `hud_scale` | `1.0` | `0.5 – 2.0` | HUD render scale. |
