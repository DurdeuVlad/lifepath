# Lifepath 1.0 — Public API Freeze (M10-1)

This document is the enumerated public surface of the 1.0 release. Anything a
player, modpack, datapack, or compat module can touch appears here; anything
absent is internal and may change without notice.

**Stability classes**

| Class | Guarantee |
|---|---|
| **Frozen** | Breaking changes require a `DATA_VERSION` bump or a minor+ release note. |
| **Stable** | May gain entries/fields; existing names and semantics won't change in 1.x. |
| **Internal** | No guarantee. May change in any release. |

"Breaking change" = a change that silently alters or drops existing behavior
for downstream consumers: renamed ids/keys/fields, changed wire formats,
tightened semantics, removed files.

---

## 1. Data domain contract (datapack surface) — Frozen

Every content domain loads `data/<any-namespace>/<domain>/<name>.json`,
**direct children only** (nested paths belong to their own domain). Files are
JSON; the entry id is `<namespace>:<name>`. Malformed files ERROR and are
skipped; unknown references warn and resolve to nothing. Datapack reload
(`/reload`, `/lifepath reload`) re-reads all domains.

| Domain path | File contract | Reference keys |
|---|---|---|
| `species` | SpeciesDefinition file: `display_name`, `description?`, `visibility` (`normal`\|`hidden`), `selection` (`open`\|`unlocked`\|`admin_only`), `passive_abilities[]`, `active_abilities[]`, `min_aptitudes{}`, `resources[]`, `diet_rules?`, `mob_dispositions?`, `capacity_multiplier` | abilities, resources, diet, relation, skill ids |
| `specialization` | `display_name`, `description?`, `starting_skills{}`, `aptitudes{}`, `xp_modifiers{}`, `decay_modifiers{}`, `protected_floors{}`, `signature` (ability id or list), `capacity_multiplier` | skill, ability ids |
| `skill` | `display_name`, `category` (`gathering`\|`crafting`\|`physical`\|`knowledge`), `max_level`, `level_curve`, `milestones[]` | level_curve id |
| `level_curve` | curve definition (xp-per-level table/formula) | — |
| `xp_source` | activity → skill mapping with `required_tags`, `per_subject`, `per_tag` weighting | skill ids, activity type ids |
| `ability` | `display_name`, `trigger` (`passive`/`active`/`event`/`damage_taken`), `conditions` SpecNode, `target` SpecNode, `actions[]`, `cooldown?`, `cost?`, `events[]` | ability vocabulary ids, resource/skill params |
| `resource` | `display_name?`, `min`, `max`, `default?`, `regen_per_second?`, `bands[]` (`range`, `name`, `effects[]`, `actions[]`) | effect/ability ids |
| `diet` | food rules (allowed item ids/`#tags`) | item ids |
| `relation` | mob disposition rules | entity-type ids/tags |
| `condition` | `display_name`, `abilities[]`, `diet_rules?`, `resources[]`, `stages[]` (`id`, `abilities[]`, `advance_events[]`, `advance_count`, `advance_after_seconds`), `acquisition[]`, `cures[]` | ability/resource/diet ids, entity tags, activity types |
| `attunement` | `display_name`, `abilities[]`, `acquisition[]` (`event`/`item`/`damage_type`/`admin` rules), `removal[]` | ability ids, activity types, item/damage-type ids |
| `item_weight` | flat `{<item-id>|#<item-tag>: weight}` map files, merged at query | item ids/tags |
| `unlock` | `display_name`, `description?`, `unlocks[]` (gated content ids), `sources[]` (`item`/`advancement`/`event`/`admin`) | species ids (today), item/advancement/activity-type ids |

Shipped ids under `lifepath:` are **Stable** — new content may add ids; shipped
ids won't be renamed or silently re-purposed in 1.x.

### Shipped content ids (frozen names)

- **species** (15): `amphibian anima automaton celestial dragonborn dwarf enderian goliath hellborn human iceborn phantom phoenix sylvian undead`
- **specialization** (13): `blacksmith cook engineer explorer farmer fisherman herbalist hunter laborer lumberjack miner scholar wanderer`
- **skill** (13): `archery athletics cooking defence engineering farming fishing foraging hunting mining scholarship smithing woodcutting`
- **condition** (2): `lycanthropy vampirism`
- **attunement** (3): `air earth lightning`
- **unlock** (3): `celestial_blessing phantom_touch phoenix_contract`
- **resource** (4): `blood load phantom_form temperature`
- **ability** (79 files) — every `ability/*.json` ships a frozen `lifepath:<file>` id.
- **diet** (3), **relation** (3), **item_weight** (1), **xp_source** (14), **level_curve** (loader present; no shipped files yet).

### Shipped tag ids (`data/lifepath/tags/`) — Stable (additive)

Content and producers reference these; datapacks may append members.

- **block**: `click_harvest`, `forageable`, `growable`, `harvestable`, `heat_sources`, `minable`, `rare_crop`, `smithing_workstations`, `valuable_ores`, `vegetation`
- **entity_type**: `animals`, `bloodsuckers`, `undead`, `wolves`
- **item**: `cookable_foods`, `coolants`, `fishing_junk`, `fishing_treasure`, `forged_outputs`, `scholarly`, `smithing_materials`, `smithing_tier_{diamond,gold,iron,netherite}`, `smithing_tools`, `undead_foods`
- **mob_effect**: `nature_purifiable`
- **worldgen/biome**: `arid`, `cold`

Lang keys (`lifepath.*`, e.g. `lifepath.skill.<id>.milestone_<n>`, item-tag
names, screen chrome) and `assets/lifepath/**` (icon, screen textures) are
convention-stable for resource packs; individual asset contents may change.

## 2. Activity event vocabulary — Frozen

`ActivityEvent` types producers emit / abilities & xp_sources match on
(`event.type` is an id; `sourceId` carries the acted-upon entity/block/item;
`tags` carry its registry tags):

`lifepath:mining`, `lifepath:farming`, `lifepath:smithing`, `lifepath:fishing`,
`lifepath:crafting`, `lifepath:combat` (kill; `sourceId` = victim type),
`lifepath:archery` (projectile kill), `lifepath:defence` (hostile hit survived;
`sourceId` = attacker type).

`Cause` enum: `PLAYER`, `NON_PLAYER`, `UNKNOWN`, `SYSTEM` — **Frozen**.

## 3. Ability vocabulary — Stable

Registered under `lifepath:` in `AbilityVocabulary`/`Builtin*`; datapack
abilities reference these; Java mods may `registerCondition/Action/Target`
their own ids.

- **Triggers**: `passive` (`interval_ticks`), `active`, `event` (`events[]`), `damage_taken` (`multiplier`).
- **Conditions** (22): `always`, `has_resource`, `attacker_entity`, `biome_tag`, `block_nearby`, `condition_stage`, `damage_amount`, `damage_type` (`id` = id or `#tag`), `daylight`, `dimension`, `entity_nearby`, `equipment_contains`, `has_condition`, `health_threshold`, `inside_block`, `inventory_contains`, `night`, `on_fire`, `resource_threshold` (`resource`,`op`,`value`), `skill_level`, `submerged`, `weather`. Combinators: `all`, `any` on `conditions` nodes.
- **Targets**: `self`, `entities_in_radius` (`radius`,`entity` idOrTag,`living_only`), `blocks_in_radius` (`radius`,`block` idOrTag).
- **Actions** (18): `apply_effect`, `consume_item`, `damage`, `debug_log`, `freeze_water`, `grant_xp`, `grow_blocks`, `heal`, `highlight_entities`, `ignite`, `modify_attribute`, `modify_resource`, `play_sound`, `random_teleport`, `remove_effect`, `resource_delta`, `spawn_particle`, `toggle_resource`.

## 4. Commands — Frozen names + argument shapes

Root `/lifepath`. Subcommands: `version`, `reload` (admin), `character
get|describe|reset <player>` (admin), `cooldown` (admin debug), `debug`
(admin), `specialization get|set` (admin), `species get|set <player> <id>`
(admin override) + `species choose <id>` (player-facing, enforces
`visibility`/`selection`), `condition get|add|remove|stage` (admin),
`attunement` (admin), `unlock get|add|remove <player> <unlock-id>` (admin).
Suggestions draw from the live registries. **Breaking** = renaming a literal
or changing argument order/type.

## 5. Config surface — Frozen file names; Stable keys

`config/lifepath/<file>.toml`, one file per spec id. Unknown keys warn;
unparseable files rename to `<name>.toml.invalid` and defaults apply.

| File | Notable keys |
|---|---|
| `general.toml` | `debug_logging` |
| `character.toml` | `flush_interval_ticks` |
| `skills.toml` | `band_*` (7 tiers), `global_xp_multiplier`, per-skill `*_xp_multiplier`, `aptitude_{d..s}_{xp,decay}_multiplier`, `unmapped_sources_award_xp`, decay `enabled`/`grace_hours`/`maintenance_minutes` |
| `abilities.toml` | `enabled`, `passive_interval_ticks`, `nearby_max_radius`, `cooldown_multiplier`, `persist_min_seconds` |
| `resources.toml` | `tick_interval_ticks` |
| `diminishing.toml` | `enabled`, `window_hours`, `tier_{1,2}_{count,multiplier}`, `tier_3_multiplier`, `signature_cap` |
| `encumbrance.toml` | `enabled`, `capacity`, `default_item_weight`, `scan_interval_ticks` |
| `client.toml` | client presentation options (HUD) — client-side only |

## 6. Networking — Frozen channel ids

S2C payloads only (clients never mutate authoritative state):
`lifepath:sync/character`, `sync/identity`, `sync/skills`, `sync/resource`,
`sync/cooldown`, `feedback`, `ability/highlight`. C2S: `lifepath:ability/activate`
(the keybind → server re-validates everything). Packet contents are
**Internal** — third parties must not parse them; mods extend via
events/data, not wire sniffing.

## 7. Saved-data schema — Frozen

Character state lives in the `lifepath:character_attachments` attachment,
encoded by the `PlayerCharacterData` codec: `dataVersion` (currently **2**),
`speciesId`, `specializationId`, `skills{}`, `traits[]`, `conditions{}`
(id→state), `attunements[]`, `unlocks[]` (gated content ids), `resources{}`,
`cooldowns{}`. Unknown list entries sanitize against `ContentIndex` on load
(dropped with WARN). Every schema change ships a `CharacterMigrations`
v(n)→v(n+1) step — the **migration contract**: old saves always load.

## 8. Java extension points

**Public** (code against these):
- `AbilityVocabulary.register{Condition,Action,Target}` + `ConditionEvaluator`/`ActionExecutor`/`TargetResolver` functional interfaces + `EvalContext`/`TargetContext`.
- `ActivityDispatcher` — `register(Identifier type, Listener)`, `registerAny`, `publish(ActivityEvent)`; `ActivityEvents` factories; `ActivityEvent`/`ActivityTypes`/`Cause`.
- `ExternalActivityAdapter` + `ExternalAdapterRegistry` — register via the `lifepath:adapter` Fabric entrypoint (`fabric.mod.json`).
- `LifepathContent` read accessors (`species()`, `abilities()`, … `get/all/contains`) + `exists(domain, id)` domain names.
- `LifepathConfig.define(id, ConfigSpec)` / `ConfigSpec` — adapter mods may ship their own `config/lifepath/<id>.toml`.
- `UnlockService.grant/revoke/isUnlocked`, `ConditionService`, `AttunementService` mutation entry points (server-side only).

**Internal** (do not code against): `AbilityEngine` internals, `*Commands`
implementations, `CharacterPersistence`/`CharacterManager` internals, mixin
classes, `network/*` payloads, `client/*` rendering, `Builtin*`/`Vanilla*`
registrations (their *ids* are public; their code is not), `perf/*`,
`util/*`.

## 9. Flagged risks / known sharp edges

- **`unlocks[]` dual semantics**: ids resolve as abilities in
  `ownedAbilities` AND as gated content for `selection:unlocked`. A species
  and ability sharing an id both fire — validation warns on the collision.
  Frozen as-is for 1.0; a future `unlocks_v2` may split the namespaces.
- **`selection:"admin_only"`** species are never player-choosable; admins use
  `species set` (override). This is deliberate; the M9-4 specials use
  `unlocked` so their grant path is data-declared.
- **`content_validation` is a report**, not a gate: bad refs warn, never
  block. Downstream must not depend on it failing a load.
- **Milestone lang keys** (`lifepath.skill.<id>.milestone_<n>`) are
  convention-only — skills without them ship fine.
- `entity`/`block` params on AoE targets accept `#tag` — tag absence degrades
  to "matches nothing," not an error.

## 10. Breaking-change audit

No planned-but-unflagged breaks found in review. Watch-list for post-1.0:
the `unlocks[]` duality (§9), `AbilityDefinition.Kind` additions (additive,
safe), and `SpecNode` param-name collisions with the `type` discriminator
(fixed pattern: params are never named `type` — keep it that way).
