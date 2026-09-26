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
  "target": {"type": "lifepath:blocks_in_radius", "radius": 7,
             "tag": "lifepath:growable"},
  "actions": [{"type": "lifepath:grow_blocks", "growth_rolls": 3}],
  "cost": {"resource": "lifepath:mana", "amount": 10},
  "cooldown": {"seconds": 240},
  "resource_interactions": [{"resource": "lifepath:mana", "per_second": -0.5}]
}
```

## Fields

| Field | Required | Meaning |
|---|---|---|
| `display_name` | yes | Human label (future UI). |
| `enabled` | no, default `true` | `false` keeps the def registered (refs resolve, ownership counts) but every trigger path returns `DISABLED`. |
| `trigger` | yes | `{"type": "active"\|"passive"\|"event"}`. `passive` honors `interval_ticks` (default 20). `event` requires `events[]` (activity-type ids, e.g. `lifepath:mining`, `lifepath:resource_band_enter`). `events[]` on a non-event trigger warns and is ignored. |
| `conditions` | no | `all[]` must ALL pass; `any[]` (if present) needs at least one pass. Empty = always true. |
| `target` | yes | One spec node resolving the target set. |
| `actions` | yes | Spec nodes run per resolved target, in order. Empty warns (cooldown-only ability). |
| `cost` | no | `{resource, amount > 0}` — gated before target resolution, spent only when actions ran. Unmaterialized resources pay from their definition default. |
| `cooldown` | no | `{seconds > 0}` — stamped on successful execution only; governed by `abilities.toml` multiplier + persistence threshold. |
| `resource_interactions` | no | `[{resource, per_second}]` — PASSIVE-only; scales by real elapsed time between evals. |

Spec nodes are `{"type": "<id>", ...params}`; params are owned by the
registered primitive, not the engine — the schema of params is per-type.

## Validation contract

Load collects **every** error and fails the file with one joined message
per problem class:

- unknown `type` in any condition / target / action / resource-band action;
- `resource` / `skill` params (plus `cost.resource`, `resource_interactions[].resource`) naming no loaded definition;
- `event` trigger with empty `events[]`;
- codec violations (non-positive `cooldown.seconds`/`cost.amount`, bad ranges, non-finite numbers, malformed JSON).

Registry-backed params (`effect`, `item`, `entity`, `block`, `sound`,
`particle`, `attribute`, `tag`…) are intentionally **not** checked at decode —
vanilla registries aren't guaranteed populated on every decode path.
Evaluators resolve them fail-closed at use time (unknown → the node no-ops
with a log). New primitives must follow the same rule.

## Built-in vocabulary

Conditions (`lifepath:` namespace unless noted): `always`, `has_resource`
(`resource`, `min`), `resource_threshold` (`resource`, `op`, `value`),
`skill_level` (`skill`, `op`, `value`), `health_threshold`, `biome_tag` (`tag`),
`dimension` (`id`), `daylight`, `night`, `weather` (`value`), `submerged`,
`on_fire`, `block_nearby` (`block`, `radius`), `entity_nearby` (`entity`,
`radius`), `inventory_contains` (`item`, `count`), `equipment_contains`.

Targets: `self`, `entities_in_radius` (`radius`, `entity`?, `living_only`?),
`blocks_in_radius` (`radius`, `tag`?).

Actions: `grant_xp` (`skill`, `amount`), `resource_delta` (`resource`,
`amount`), `modify_resource` (`resource`, `delta`\|`set_to`), `debug_log`
(`message`), `apply_effect` (`effect`, `duration`, `amplifier`),
`remove_effect`, `modify_attribute` (`attribute`, `operation`, `value`,
`duration_ticks`?), `damage` (`amount`, `source`?), `heal` (`amount`),
`grow_blocks` (`growth_rolls`), `freeze_water` (`temporary`?),
`highlight_entities` (`duration_ticks`, `visibility`), `consume_item`
(`item`, `count`), `play_sound` (`sound`), `spawn_particle`.

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
5. A node referencing content declares it via `resource`/`skill` param keys
   so load validation sees it. Other registry refs resolve lazily fail-closed.

## Shipped synthetic abilities

`data/lifepath_test/` carries the TIMELINE §7 test set (never referenced by
production content; granting them via unlocks is the test seam):
`test_passive_condition` (passive + `has_resource`),
`test_active_cooldown` (active + 60s cooldown),
`test_aoe_target` (entities_in_radius + highlight),
`test_resource_conditioned` (threshold gate + cost on `lifepath_test:test_focus`),
`test_state_change` (block_broken event + modify_resource).
