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
`excluded_subjects` (sourceIds that never match).

**Matching model.** Producers emit `ActivityEvent`s carrying `type`
(the activity id), `sourceId` (what was acted on — broken block id, killed
entity id, crafted item id, fish id), `tags` (registry tags on that subject),
`cause`, `timestamp`, `attributes`. Award resolution: `per_subject[sourceId]`
wins; else the first `per_tag` entry whose tag the event carries; else
`base_xp`. Unmapped events fall back per `skills.toml:
unmapped_sources_award_xp`. Built-in activity ids: `lifepath:mining`,
`farming`, `smithing`, `fishing`, `crafting`, `combat`, `archery`, `defence`,
`foraging`, `woodcutting`, `cooking`, `athletics`, `hunting`, `scholarship`,
`engineering`, `decay` (internal).

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
  "capacity_multiplier": 1.0
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
encumbrance capacity (default `1.0`).

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
  "capacity_multiplier": 1.0
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

Top level: `display_name`, `enabled` (default `true`), `trigger`,
`conditions`, `target`, `cost`, `cooldown`, `actions`,
`resource_interactions`. `trigger.type` is `passive` (repeat sweep,
`interval_ticks`), `active` (player-activated via
`lifepath:ability/activate`), `event` (fires on `events[]` activity ids),
`damage_taken` (modifies incoming damage, `multiplier`).
`conditions` composes `{all:[…]} ∩ {any:[…]}` — every `all` node plus at
least one `any` node when present. Conditions/actions/target are SpecNodes —
`{ "type": "<primitive id>", ...params }` where the primitive set
(22 conditions, 18 actions, 3 targets) is enumerated in `docs/ABILITIES.md`.
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
{ "allowed": ["minecraft:beef", "#lifepath:undead_foods"] }
```

`allowed` mixes item ids and `#tags`. While a species/condition diet applies,
non-listed foods yield zero nutrition. Referenced by `diet_rules`.

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
`chance`), `item` (use), `admin`. Admin: `/lifepath condition
add|remove|stage`.

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
`encumbrance.toml`.

## Shipped tags (extend these from your pack)

Blocks: `lifepath:minable`, `forageable`, `harvestable`, `click_harvest`,
`growable`, `vegetation`, `rare_crop`, `valuable_ores`, `heat_sources`,
`smithing_workstations`. Entities: `lifepath:animals`, `undead`,
`bloodsuckers`, `wolves`. Items: `lifepath:cookable_foods`, `scholarly`,
`smithing_*` (materials/tools/tier_iron/gold/diamond/netherite),
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
