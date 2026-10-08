# Lifepath Datapack API (1.0)

How content is defined. **Java = mechanics, data = content** — every gameplay
object below is a JSON file under `data/<namespace>/<domain>/<name>.json` in any
datapack; the file path sets the identifier (`data/example/skill/foo.json` →
`example:foo`). Each domain scans **direct children only** — nested paths belong
to their own domain (level curves live at `skill/curve/`). Lifepath's own
content lives under the `lifepath` namespace; packs add alongside it.

**Load & validation** — datapacks load on world start and `/reload` (also
`/lifepath reload`). Each file is decoded by its codec; a file that fails to
parse is skipped with an error naming the file. Cross-references (a skill's
`level_curve`, a species' `abilities`, an xp_source's `skill`) are then
validated: unknown ids warn and are ignored rather than crashing. Content
removed between sessions is dropped from saved characters on load with a warn —
saves never brick.

The canonical machine listing of every registered domain, primitive, shipped
tag and payload id is `docs/API.md`. Primitive semantics (what each
condition/action/target does) live in `docs/ABILITIES.md`; balance knobs in
`docs/CONFIGURATION.md`; save-format history in `docs/MIGRATIONS.md`.

## Common field: `icon`

Every UI-rendered domain — `species`, `specialization`, `skill`, `ability`,
`attunement`, `condition`, `resource` — accepts an optional `icon` string:

```jsonc
"icon": "species/human"                          // → lifepath:textures/gui/species/human.png
"icon": "othermod:textures/gui/species/human.png" // explicit id, taken literally
```

A bare path is shorthand resolved under `lifepath:textures/gui/` (`.png`
appended if absent); an explicit `namespace:path` is used verbatim. The value
is a *reference*: the texture lives under `assets/` and is read client-side
only — dedicated servers never need it. Resolution order in the UI is
declared icon → generic per-domain placeholder (`textures/gui/placeholder/
<domain>.png`) → text-only. A malformed string warns in the validation report
and degrades to no-icon; it never fails the file.

## `skill/` — skill definition

```json
{
  "display_name": "Mining",
  "description": "Breaking natural stone and ore.",
  "improve_hint": "Mine valuable natural blocks and ores.",
  "category": "gathering",
  "max_level": 100,
  "level_curve": "lifepath:standard",
  "milestones": [{ "level": 20, "description_key": "lifepath.skill.mining.milestone.20" }],
  "xp_sources": [],
  "effects": [],
  "passive_scaling": {}
}
```

Required: `display_name`. Optional: `description`, `improve_hint`,
`description_key`, `category` (`gathering`/`crafting`/`combat`/`physical`/
`knowledge`), `max_level` (default 100), `level_curve` (id of a `level_curve`
file; omitted = default curve), `milestones` (ordered `{level,
description_key}` — keys resolve in `assets/<ns>/lang/*.json`), `xp_sources`,
`effects`, `passive_scaling`.

## `xp_source/` — activity → XP mapping

```json
{
  "activity": "lifepath:mining",
  "skill": "lifepath:mining",
  "player_caused_only": true,
  "base_xp": 0.02,
  "per_subject": { "minecraft:diamond_ore": 2.0 },
  "per_tag": { "minecraft:diamond_ores": 2.0 },
  "required_tags": ["lifepath:minable"],
  "excluded_subjects": ["minecraft:stone"]
}
```

Fields: `activity` (activity-type id, required), `skill` (skill id, required),
`player_caused_only` (default `false` — ignore events the player didn't cause),
`base_xp` (default `0.0` — award when no per-entry matches; `0` = gate-only),
`per_subject` (exact `sourceId` → xp), `per_tag` (event tag → xp),
`required_tags` (event must carry **all** of these or the rule doesn't fire),
`excluded_subjects` (sourceIds that never match),
`conditional_bonuses` (optional player-state multipliers — see below).

**Matching model.** Producers emit `ActivityEvent`s carrying `type`
(the activity id), `sourceId` (what was acted on — broken block id, killed
entity id, crafted item id, fish id), `tags` (registry tags on that subject),
`cause`, `timestamp`, `attributes`. Award resolution: `per_subject[sourceId]`
wins; else the first `per_tag` entry whose tag the event carries; else
`base_xp`. Unmapped events fall back per `skills.toml:
unmapped_sources_award_xp`.

`conditional_bonuses` multiplies the resolved award when the event carries
`tag` **and** the performer satisfies `when` — a standard ability condition
(`player_faction`, `has_condition`, `season`, …). First matching bonus wins;
unknown or throwing conditions warn once and skip. Shipped example:
vampires (`vampirism:vampire` faction or the `lifepath:vampirism`
condition) forge `smithing_tier_steel` at ×1.5.

```json
"conditional_bonuses": [
  { "tag": "lifepath:smithing_tier_steel", "multiplier": 1.5,
    "when": { "type": "lifepath:player_faction",
              "faction": "vampirism:vampire" } }
]
```

Built-in activity ids: `lifepath:mining`,
`farming`, `smithing`, `fishing`, `crafting`, `combat`, `archery`, `defence`,
`foraging`, `woodcutting`, `cooking`, `athletics`, `hunting`, `scholarship`,
`engineering`, `decay` (internal).

## `outcome_rule/` — skill band → output modifiers

Scales what an activity **produces** based on the performing player's rank
band in `skill`. Files at `data/<ns>/outcome_rule/<name>.json`.

```json
{
  "skill": "lifepath:smithing",
  "activity": "lifepath:smithing",
  "subjects": ["minecraft:cake"],
  "required_tags": ["lifepath:forged_outputs"],
  "excluded_subjects": ["minecraft:shears"],
  "bands": {
    "untrained": { "output_count_mult": 0.75, "failure_chance": 0.2,
                   "failure_count_mult": 0.5, "quality_tier": "crude",
                   "anvil_cost_mult": 1.25 },
    "master":    { "output_count_mult": 1.25, "quality_tier": "masterwork",
                   "sign_items": true }
  }
}
```

Fields: `skill` (required — the performer's level in this skill picks the
band), `activity` (optional pin — absent matches any activity), `subjects`
(optional allowlist on `sourceId` — use it to split skills that share an
activity, e.g. cooking vs engineering on `lifepath:crafting`),
`required_tags` (event must carry all), `excluded_subjects` (denylist).

Per-band modifiers (all optional, absent = identity):

- `output_count_mult` (0–8, default 1) — take-seams round to a whole count
  with a minimum of 1; drop-seams (block/entity/fishing loot) roll it
  probabilistically per stack and **can reduce a drop to zero**.
- `failure_chance` (0–1) — take-seams only: consume inputs, produce a
  `crude` partial result at `failure_count_mult`× count.
- `quality_tier` — `crude`/`poor`/`fine`/`masterwork` stamped into
  `custom_data` + lore; Overgeared forges map it onto native
  `ForgingQuality`. `"standard"` = explicit vanilla baseline (no stamp).
- `sign_items` — adds `lifepath:crafter` data + "Crafted by" lore.
- `anvil_cost_mult` (0–4) — scales the vanilla anvil XP-level cost.
- `junk_upgrade_chance` (0–1) — fishing only: chance a `lifepath:fishing_junk`
  catch is replaced by a rolled `#minecraft:fishes` pick.

Matching: when several rules match, most specific wins —
`subjects` count ×10 + `required_tags` count + activity pin, then stable id
order. Band keys are the rank-band names (`untrained` … `legendary`);
unknown keys warn once at load, never fail the file.

Gates & clamps (`config/lifepath/skills.toml`): `outcome_scaling_enabled`,
`outcome_max_failure_chance` (.5), `outcome_min_count_mult` (.25),
`outcome_max_count_mult` (2.0). Applied at resolve time, not load.

Attribution: outcomes follow the **performing player** — taker, breaker,
killer (shooter on projectile kills), fishing-hook owner, Overgeared
hammer-swinger. Player-placed blocks and automation get no scaling.

## `skill/curve/` — XP thresholds (domain id `level_curve`)

Nested path: `data/<ns>/skill/curve/<name>.json` — referenced by `skill`'s
`level_curve` field.

```json
{ "thresholds": [0, 40, 95, 165] }
```

`thresholds[L]` is the *cumulative* XP to reach level `L`. Must be non-empty,
`thresholds[0] == 0`, strictly increasing; max level is `length - 1`. Shipped:
`lifepath:default`.

## `species/` — playable species

```json
{
  "display_name": "Phoenix",
  "description": "What the selection card shows.",
  "selection": "unlocked",
  "visibility": "normal",
  "min_aptitudes": {},
  "diet_rules": "lifepath:necrophage",
  "mob_dispositions": "lifepath:undead_kin",
  "resources": ["lifepath:rebirth"],
  "passive_abilities": ["lifepath:phoenix_passive"],
  "active_abilities": ["lifepath:phoenix_flare"],
  "capacity_multiplier": 1.0,
  "strengths": ["Fire heals you", "Rise again when felled"],
  "weaknesses": ["Water quenches your inner fire"],
  "scale": 1.0
}
```

Required: `display_name`. `selection`: `open` (always choosable),
`unlocked` (needs the species id present in the player's unlocks — see
`unlock/`), `admin_only` (only `/lifepath species set`). `visibility`: `normal`
or `hidden` (omitted from the selection screen until the player holds the
unlock — then it surfaces as a normal card).
`diet_rules`/`mob_dispositions` are single ids into the `diet`/`relation`
domains. `resources`/`passive_abilities`/`active_abilities` are id lists
validated against `resource`/`ability`. `capacity_multiplier` scales
encumbrance capacity (default `1.0`). `scale` (optional, `0.1`–`8.0`) sets
the player's physical size through Pehkui's `pehkui:base` scale when that
mod is installed — shipped defaults: dwarf `0.7`, goliath `1.4`. Applied on
species set, re-applied on join and respawn (the bridge also marks the
value persistent so it survives death). Species without `scale` reset to
`1.0`; without Pehkui the field is ignored entirely — no classes load, no
crash, behavior identical to before.
`strengths`/`weaknesses` are free-text lists the selection picker shows as
green `+` / red `-` lines — the player's "what's good / what's bad" summary
before committing (diet, environment, fragility). Keep each line short.
They travel as translatable components: bundled content is localized under
`lifepath.species.<id>.strength.<n>` / `.weakness.<n>` (see TRANSLATIONS.md),
while custom prose rides as the literal fallback — a datapack needs no lang
file for its text to render.

## `specialization/` — specialization preset

```json
{
  "display_name": "Miner",
  "description": "…",
  "starting_skills": { "lifepath:mining": 5 },
  "aptitudes": { "lifepath:mining": "b" },
  "xp_modifiers": { "lifepath:mining": 1.1 },
  "decay_modifiers": {},
  "protected_floors": {},
  "signature": ["lifepath:miner_signature"],
  "capacity_multiplier": 1.0,
  "strengths": [],
  "weaknesses": []
}
```

Required: `display_name`; all others optional. `signature` is a list of
ability ids granted while specialized. `starting_skills` maps skill id → starting level;
`aptitudes` map skill id → grade `d`–`s` (drives the per-grade multipliers in
`skills.toml`); `xp_modifiers`/`decay_modifiers` map skill id → multiplier;
`protected_floors` maps skill id → level floor decay can't cross.

## `ability/` — data-driven ability

```json
{
  "display_name": "Night Eyes",
  "description": "Dark-adapted eyes read the night.",
  "enabled": true,
  "trigger": { "type": "passive", "interval_ticks": 40 },
  "conditions": { "all": [ { "type": "lifepath:night" } ] },
  "target": { "type": "lifepath:self" },
  "cost": { "resource": "lifepath:blood", "amount": 1.0 },
  "cooldown": { "seconds": 30 },
  "actions": [
    { "type": "lifepath:apply_effect", "effect": "minecraft:night_vision",
      "duration": 200, "amplifier": 0 }
  ]
}
```

Top level: `display_name`, `description` (optional; shown in the
Character screen ability tooltip — localize via
`lifepath.ability.<id>.description`), `enabled` (default `true`),
`trigger`, `conditions`, `target`, `cost`, `cooldown`, `actions`,
`resource_interactions`. `trigger.type` is `passive` (repeat sweep,
`interval_ticks`), `active` (player-activated via
`lifepath:ability/activate`), `event` (fires on `events[]` activity ids),
`damage_taken` (modifies incoming damage, `multiplier`).
`conditions` composes `{all:[…]} ∩ {any:[…]}` — every `all` node plus at
least one `any` node when present. Negation composes per-node:
`{"type": "lifepath:not", "condition": {…inner node…}}`. Conditions/actions/
target are SpecNodes — `{ "type": "<primitive id>", ...params }` where the
primitive set (24 conditions, 18 actions, 3 targets) is enumerated in
`docs/ABILITIES.md`.
`cost` is `{resource, amount}` charged per fire; `resource_interactions` is
the passive drain/regen seam (`{resource, per_second}`). Unknown primitive
types fail validation loudly; malformed params fall back to defaults.

## `resource/` — materialized resource (mana/blood/load)

```json
{
  "display_name": "Blood",
  "min": 0.0, "max": 100.0, "default": 100.0,
  "regen_per_second": -0.06,
  "bands": [
    { "name": "hungry", "range": [0.0, 25.0],
      "effects": [{ "effect": "minecraft:hunger", "amplifier": 0 }],
      "actions": [] }
  ]
}
```

Required: `display_name`, `min`, `max`. `default` seeds new characters,
`regen_per_second` applies on the `resources.toml` sweep, `bands` give
range-locked effects/actions via the normal ability machinery. Referenced by
`resource` conditions, `modify_resource`/`toggle_resource` actions, species
`resources`, condition `resources`, encumbrance's `lifepath:load`.

## `diet/` — allowed-food list

```json
{ "allowed": ["minecraft:beef", "#lifepath:undead_foods"],
  "deny_message": "Your body rejects that food — only meat nourishes you." }
```

`allowed` mixes item ids and `#tags`. While a species/condition diet applies,
non-listed foods are refused at use-start and yield zero nutrition through
the `Player.eat` backstop. Referenced by `diet_rules`.

`deny_message` (optional) replaces the stock refusal actionbar
(`lifepath.diet.denied`) — use it to say what actually nourishes.

## `relation/` — mob disposition rules

```json
{ "rules": [ { "entity": "#lifepath:animals", "disposition": "friendly" } ] }
```

`entity` accepts an id or `#entity_type tag`; `disposition` is
`neutral`/`friendly`/`hostile` (`friendly`/`neutral` suppress aggression).

## `condition/` — staged state (vampirism, lycanthropy)

```json
{
  "display_name": "Vampirism", "description": "…",
  "diet_rules": "lifepath:blood",
  "resources": ["lifepath:blood"],
  "abilities": ["lifepath:vampirism_sun_sickness"],
  "stages": [
    { "id": "fledgling",
      "abilities": ["lifepath:vampirism_night_eyes"],
      "advance_events": ["lifepath:combat"],
      "advance_count": 3,
      "advance_after_seconds": 1200 },
    { "id": "apex", "abilities": ["lifepath:vampirism_apex_regen"] }
  ],
  "acquisition": [
    { "type": "attack", "entity": "#lifepath:bloodsuckers", "chance": 0.35 },
    { "type": "admin" }
  ],
  "cures": [
    { "type": "item", "item": "minecraft:golden_apple" },
    { "type": "admin" }
  ]
}
```

A held condition unions top-level `abilities`/`diet_rules`/`resources` over
the species', plus all stages up to the current one. `diet_rules` is a single
diet id. Each stage needs an `id`; `advance_events` is a flat list of
activity-type ids counted up to `advance_count` (default 1), OR
`advance_after_seconds` elapsed in-stage — whichever fires first advances.
`acquisition`/`cures` `type`s: `attack` (damaged by entity/`#tag`, optional
`chance`), `item` (use), `admin` — plus `death` (acquisition only: fires
when the player dies; an optional `species` id gates which species it
applies to, and re-dying while the condition is held resets it to stage 0
rather than no-op'ing). Admin: `/lifepath condition add|remove|stage`.

## `attunement/` — flat affinity (air, earth, lightning)

```json
{
  "display_name": "Air", "description": "…",
  "abilities": ["lifepath:air_leap"],
  "acquisition": [
    { "type": "item", "item": "minecraft:phantom_membrane", "consume": true },
    { "type": "damage", "damage_type": "#minecraft:is_lightning", "chance": 0.6 },
    { "type": "event", "event": "lifepath:combat" },
    { "type": "admin" }
  ],
  "removal": [ { "type": "admin" } ]
}
```

No stages — held attunement grants all its `abilities`. Acquisition `type`s:
`item` (`consume` eats it), `damage` (`damage_type` id or `#tag`, `chance`),
`event` (`event` = activity id), `admin`.

## `unlock/` — gated-content grant (special species)

```json
{
  "display_name": "Phoenix Contract", "description": "…",
  "unlocks": ["lifepath:phoenix"],
  "sources": [
    { "type": "item", "item": "minecraft:totem_of_undying", "consume": true },
    { "type": "advancement", "advancement": "minecraft:end/levitation" },
    { "type": "event", "event": "lifepath:combat", "subject": "minecraft:phantom",
      "tag": "#minecraft:undead", "chance": 0.05 },
    { "type": "admin" }
  ]
}
```

`sources` fire once each, idempotently: `item` (use, optional `consume`),
`advancement` (granted on completion), `event` (`event` = activity id —
`subject` = sourceId, `tag` = required tag, `chance`), `admin` (only
`/lifepath unlock add`). The granted ids land in `data.unlocks()` and are what
`selection:"unlocked"` species check — so a species id that's *also* an
ability id would additionally grant that ability (engine treats unlock tokens
as ability ids; avoid name collisions — a startup warn fires).

## `item_weight/` — encumbrance weights

```json
{ "minecraft:stone": 1.0, "#minecraft:logs": 2.0 }
```

A flat id/`#tag` → weight map per file; feeds `lifepath:load` with
`encumbrance.toml`. Container items recurse: stacks inside a
`CONTAINER`/`BUNDLE_CONTENTS` component (bundles, shulker boxes, modded bags
reusing the vanilla components) add their weight at a contents factor —
`container_contents_factor` config (default `0.75`) or per-container
`"<id>@contents"` / `"#<tag>@contents"` entries (`0.0` ⇒ contents weigh
nothing, `1.0` ⇒ full weight). Modded bags with custom storage need the
platform capability hook — not currently wired.

## `morph_form/` — animal morph whitelist

```json
{
  "entity_type": "minecraft:fox",
  "display_name": "Fox",
  "description": "Small, quick, and suspiciously good at stealing chickens.",
  "icon": "morph/fox",
  "stats": {
    "minecraft:generic.max_health": 10.0,
    "minecraft:generic.attack_damage": 3.0,
    "minecraft:generic.movement_speed": 0.3
  }
}
```

The handpicked animal roster anima players pick from at species-select
(one form per character, locked like the species — M-2). Required:
`entity_type` (a real entity type; non-animal categories warn and the file
loads, aquatic/flying forms are out of scope by design), `display_name`.
`description` and `icon` are optional; `stats` is an attribute-id →
absolute-value map applied while morphed — `generic.max_health` must be
positive when present (proportional HP carry divides by it). Names and
descriptions translate via `lifepath.morph_form.<name>.name` / `.description`
keys like every other domain. Deleting a form file demorphs and orphans
characters that picked it — the load-time sanitize drops the reference.

## Shipped tags (extend these from your pack)

Blocks: `lifepath:minable`, `forageable`, `harvestable`, `click_harvest`,
`growable`, `vegetation`, `rare_crop`, `valuable_ores`, `heat_sources`,
`smithing_workstations`. Entities: `lifepath:animals`, `undead`,
`bloodsuckers`, `wolves`. Items: `lifepath:cookable_foods`, `scholarly`,
`smithing_*` (materials/tools/tier_stone/copper/iron/gold/steel/diamond/netherite),
`forged_outputs`, `fishing_junk`, `fishing_treasure`, `coolants`,
`undead_foods`. Effects: `lifepath:nature_purifiable`. Biomes:
`lifepath:arid`, `cold`. Vanilla tags (`minecraft:logs`, `minecraft:*_ores`,
`minecraft:is_fall`, `minecraft:undead`, …) are used everywhere ids are.

## Worked example: `mymod:mythril_ore` → Mining XP

Two files — no Java, and nothing to copy from the mod jar.

```jsonc
// 1. data/lifepath/tags/block/minable.json
// A datapack file under the lifepath namespace *extends* the shipped tag —
// packs stack on the same id, so this appends, it doesn't replace.
{ "replace": false, "values": ["mymod:mythril_ore"] }
```

```jsonc
// 2. data/mymod/xp_source/mythril.json
{
  "activity": "lifepath:mining",
  "skill": "lifepath:mining",
  "player_caused_only": true,
  "per_subject": { "mymod:mythril_ore": 1.2 },
  "required_tags": ["lifepath:minable"]
}
```

`/reload`, break the ore: the mining producer emits `sourceId:
mymod:mythril_ore` carrying its block tags (including `lifepath:minable`, so
`required_tags` passes), `per_subject` matches, 1.2 mining XP is awarded —
aptitude/diminishing/species multipliers apply on top. To reward a whole
family of modded ores instead of per-block values, use `per_tag` with a tag
your mod already ships:

```jsonc
  "per_tag": { "mymod:all_mythril_tier_ores": 1.2 }
```
