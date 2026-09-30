# Ability data format (M4-6)

Abilities are **pure data composition** — `data/<namespace>/ability/<name>.json`.
The file name becomes the ability id (`<namespace>:<name>`). Vanilla datapack
override semantics apply: same-id files in later packs win.

```json
{
  "display_name": "Verdant Bloom",
  "enabled": true,
  "trigger": {"type": "active"},
  "conditions": {
    "all": [{"type": "lifepath:biome_tag", "tag": "minecraft:is_forest"}],
    "any": []
  },
  "target": {"type": "lifepath:entities_in_radius", "radius": 5},
  "actions": [{"type": "lifepath:highlight_entities", "duration_ticks": 60}],
  "cost": {"resource": "lifepath:mana", "amount": 10},
  "cooldown": {"seconds": 240},
  "resource_interactions": [{"resource": "lifepath:mana", "per_second": -0.5}]
}
```

## Fields

| Field | Required | Meaning |
|---|---|---|
| `display_name` | yes | Human label (future UI). |
| `enabled` | no, default `true` | `false` keeps the def registered (refs resolve, ownership counts) but every trigger path returns `DISABLED`. Does NOT skip load validation — a disabled file with errors is still rejected. |
| `trigger` | yes | `{"type": "active"\|"passive"\|"event"\|"damage_taken"}`. `passive` honors `interval_ticks` (default 20, ignored on other kinds). `event` requires `events[]` (activity-type ids, e.g. `lifepath:mining`, `lifepath:resource_band_enter`). `damage_taken` evaluates synchronously inside the player's `damage()` call: passing conditions scale the incoming amount by `multiplier` (default 1.0), and `actions`/`cost`/`cooldown`/`resource_interactions` are ignored (warned at load). `events[]` on a non-event trigger warns and is ignored. `resource_interactions` likewise only apply under `passive`. |
| `conditions` | no | `all[]` must ALL pass; `any[]` (if present) needs at least one pass. Empty = always true. |
| `target` | yes | One spec node resolving the target set. |
| `actions` | no, default `[]` | Spec nodes run per resolved target, in order. Empty warns on non-`damage_taken` triggers (cooldown-only ability). |
| `cost` | no | `{resource, amount > 0}` — gated before target resolution, spent only when actions ran. Unmaterialized resources pay from their definition default. |
| `cooldown` | no | `{seconds > 0}` — stamped on successful execution only; governed by `abilities.toml` multiplier + persistence threshold. |
| `resource_interactions` | no | `[{resource, per_second}]` — PASSIVE-only; scales by real elapsed time between evals. |

Spec nodes are `{"type": "<id>", ...params}`; params are owned by the
registered primitive, not the engine — the schema of params is per-type.

## Validation contract

Load collects **every** error and fails the file with one joined message
per problem class:

- unknown `type` in any condition / target / action / resource-band action;
- `resource` / `skill` params (plus `cost.resource`, `resource_interactions[].resource`) that fail to parse as an identifier or name no loaded definition — the same scan applies to resource-band actions;
- `event` trigger with empty `events[]` — and a WARN for event ids outside the known activity-type set (the bus accepts arbitrary ids, so typos warn rather than error).

Registry-backed params (`effect`, `item`, `entity`, `block`, `sound`,
`particle`, `attribute`, tag ids under `block`/`entity`/`item`/`tag`…) are
intentionally **not** checked at decode — vanilla registries aren't guaranteed
populated on every decode path. Evaluators resolve them fail-closed at use
time (unknown → the node no-ops with a log). New primitives must follow the
same rule. A `#`-prefixed string under `resource`/`skill` is treated as a tag
reference and skipped by the content-ref scan.

## Built-in vocabulary

### Conditions (`lifepath:` namespace)

| Type | Params |
|---|---|
| `always` | — |
| `has_resource` | `resource`, `min` (default 0) |
| `resource_threshold` | `resource`, `op`, `value` |
| `skill_level` | `skill`, `op`, `level` |
| `health_threshold` | `op`, `value` |
| `biome_tag` | `tag` (biome tag id, `#` optional) |
| `dimension` | `id` |
| `daylight` / `night` | — |
| `weather` | `state`: `thunder`\|`rain`\|`clear` |
| `submerged` / `on_fire` | — |
| `block_nearby` | `block` (id or `#tag`), `radius` |
| `entity_nearby` | `entity` (id or `#tag`), `radius` |
| `inventory_contains` | `item` (id or `#tag`), `min_count` |
| `equipment_contains` | `item` (id or `#tag`), `slot`? |
| `inside_block` | `block` (id or `#tag`, required) — the block state at the player's feet |
| `attacker_entity` | `entity` (id or `#tag`) — matches `source.getAttacker()`; damage_taken only |
| `damage_type` | `id` (id or `#tag` on the damage-type registry); damage_taken only |
| `damage_amount` | `op`, `value` — compares the pre-modifier amount; damage_taken only |
| `has_condition` | `condition` (condition-def id) — true while the character holds it |
| `condition_stage` | `condition`, `stage`, `op`? (default `>=`) — compares the held stage index |
| `player_faction` | `faction` (id), `min_level`? — external faction membership via the TeamLapen bridge (Vampirism *and* Werewolves share it). Always false when the mod is absent or the bridge cut |
| `not` | `condition` (an inline spec node) — negates one condition, Origins-style; a malformed or throwing inner node counts as true → the not yields false |

### Targets

| Type | Params |
|---|---|
| `self` | — |
| `entities_in_radius` | `radius`, `entity` (id or `#tag`, optional), `living_only` (default true) |
| `blocks_in_radius` | `radius`, `block` (id or `#tag`, **required** — absent resolves to nothing), `limit` (scan cap) |

### Actions

| Type | Params |
|---|---|
| `grant_xp` | `skill`, `amount` |
| `resource_delta` | `resource`, `amount` |
| `modify_resource` | `resource`, `delta` or `set_to` |
| `toggle_resource` | `resource` — flips a two-state resource to min ↔ max (Phantom form toggle, M9-4); no-op on unknown resources |
| `debug_log` | `message` |
| `apply_effect` | `effect`, `duration` (ticks), `amplifier` |
| `remove_effect` | `effect` (id or `#tag`) |
| `modify_attribute` | `attribute`, `operation` (`add_value`\|`add_multiplied_base`\|`add_multiplied_total`), `value`, `duration_ticks`? |
| `damage` | `amount`, `source`? (`magic`\|`starve`\|`fall`\|`fire`\|`wither`\|`drown`, else player-attack) |
| `heal` | `amount` |
| `ignite` | `duration_ticks`? (default 80) — sets burning; never shortens an existing burn |
| `random_teleport` | `range`? (default 8, max 64), `attempts`? (default 16, max 64) — chorus-fruit blink to a random safe spot (never into walls, void, or liquid); no-op if every sample fails |
| `grow_blocks` | `growth_rolls` |
| `freeze_water` | `temporary` (default true) |
| `highlight_entities` | `duration_ticks`, `visibility` (`self`\|`global`) |
| `consume_item` | `item` (id or `#tag`), `count` |
| `play_sound` | `sound`, `volume`?, `pitch`? |
| `spawn_particle` | `particle`, `count`?, `dx`/`dy`/`dz`?, `speed`? |

## Java extension contract

New mechanics register by id — no subclasses, no content switches:

```java
AbilityVocabulary.registerCondition(id, (ctx, params) -> boolean);
AbilityVocabulary.registerTarget(id,    (ctx, params) -> List<TargetContext>);
AbilityVocabulary.registerAction(id,    (target, ctx, params) -> void);
```

Contract rules (also in the `AbilityVocabulary` javadoc):

1. Register during mod init — files referencing unregistered types fail to load.
2. Fail closed: malformed params → false / no-op; never throw for data errors.
3. Nullability: `ctx.self()` may be null; `target.entity()` null for block
   targets; `target.data()` null for model-less targets. Guard both.
4. Mutate character state through `ResourceService` / `SkillXpService` /
   `CooldownService` — never the model maps directly.
5. A node referencing a Lifepath content id declares it via the `resource` /
   `skill` param keys so load validation sees it — if your param means
   something else, name it differently or `#`-prefix it (tag values skip the
   scan). Other registry refs resolve lazily fail-closed.

## Shipped synthetic abilities

`data/lifepath_test/` carries the TIMELINE §7 test set (never referenced by
production content; granting them via unlocks is the test seam):
`test_passive_condition` (passive + `has_resource`),
`test_active_cooldown` (active + 60s cooldown),
`test_aoe_target` (entities_in_radius + highlight),
`test_resource_conditioned` (threshold gate + cost on `lifepath_test:test_focus`),
`test_state_change` (`lifepath:mining` event + `modify_resource`).

## Species rule domains (`data/<ns>/diet/`, `data/<ns>/relation/`)

M5-4 added two generic content domains a species file references via
`diet_rules` / `mob_dispositions` ids. Both degrade to vanilla behavior when
absent, malformed, or unloaded.

### `diet/<name>.json`

```json
{"allowed": ["#lifepath:undead_foods", "minecraft:golden_apple"]}
```

`allowed` is a non-empty list of item ids or `#item-tags`. A character with
the rule gains **zero nutrition** (no hunger/saturation) from unlisted items;
the item is still eaten and its vanilla food side-effects apply. Enforced by
wrapping the `HungerManager.eat` call inside `PlayerEntity.eatFood`
(server-side only). Extend by datapack tags — no Java needed.

### `relation/<name>.json`

```json
{"rules": [{"entity": "#lifepath:undead", "disposition": "neutral"}]}
```

`rules` map entity ids or `#entity_type-tags` to `neutral` | `friendly` |
`hostile`, evaluated in order (first match wins). `neutral`/`friendly`
suppress the mob's target predicate (`TargetPredicate.test` — one mixin seam
covers all goal-driven targeting); `hostile` is explicit vanilla. Suppression
is evaluated on the *target* player's species — an Undead player is ignored
by `#lifepath:undead` mobs; everyone else is not.
