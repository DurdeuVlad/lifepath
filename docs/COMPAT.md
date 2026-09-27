# Compatibility model (M7-1)

Lifepath is a progression layer over other mods' gameplay. External systems
provide content; Lifepath provides XP/levels/aptitude/abilities on top. Nothing
here replaces a foreign loop.

## Priority order applied (TIMELINE §10)

| Level | Used for |
|---|---|
| 1 — vanilla/Fabric events | `onTakeItem` result slots, block-break, fishing bobber loot, entity kills, damage |
| 2 — MC tags | `#lifepath:smithing_tools`, `#lifepath:smithing_workstations`, `#lifepath:minable`, `#lifepath:forged_outputs`, … (all `data/lifepath/tags/`) |
| 3 — datapack integration | xp_source files match normalized events by id/tag; any mod item enters progression by adding a tag entry — zero Java |
| 4 — public API | none needed so far |
| 5 — optional compat module | `compat/overgeared/` — self-gated adapter |
| 6 — mixins | only vanilla classes are ever mixed (result slots, entity damage) |

## The `lifepath:adapter` entrypoint

Any mod or bridge can translate its events into normalized Lifepath activity:

```json
"entrypoints": { "lifepath:adapter": [ "com.example.MyAdapter" ] }
```

Implement `io.github.durdeuvlad.lifepath.compat.ExternalActivityAdapter`.
Discovery is per-entrypoint `Throwable`-isolated — a bridge built against a
different version logs an error and is skipped, never crashes init.

## Overgeared adapter (`compat/overgeared/`)

Overgeared ships **no Fabric build for 1.21.1** (1.20.1 only; 1.21.1 is
Forge/NeoForge) and exposes no stable Fabric event API — so the shipped
adapter is deliberately declarative:

- Watches normalized `lifepath:crafting` events; when the result id is
  `overgeared:*` or carries `#lifepath:forged_outputs`, republishes it as
  canonical `lifepath:smithing` activity (workstation
  `overgeared:smithing_anvil`, `compat=overgeared` marker). Existing smithing
  xp_source data then awards Smithing XP — datapack decides amounts.
- Overgeared anvils ship as `required:false` entries in
  `#lifepath:smithing_workstations`; cast items in `#lifepath:forged_outputs`.
- A future Fabric port/bridge can emit forging events directly through the
  entrypoint instead — the shipped adapter only covers the declarative slice.

**Grep check:** `overgeared` appears only under `compat/overgeared/` and
`data/` tags — zero external ids in generic services.

## Graceful-degradation matrix

| Scenario | Result |
|---|---|
| Overgeared absent | `register()` logs one debug line, wires nothing; vanilla paths untouched (boot-verified) |
| Overgeared present | dispatcher subscription active; crafting events with forged results translate to smithing XP |
| Item id changed/renamed | tag entries simply miss; `required:false` entries never fail tag load |
| Recipe removed | fewer events fire; nothing errors |
| Adapter API drift (bridge built vs different Lifepath) | entrypoint throws `LinkageError` → caught per-adapter, error logged, init continues |
| Malformed adapter (`id()` throws) | `safeId` fallback id used for logging, adapter skipped |

## Tag-driven coverage for modded content

Pack authors add foreign items to Lifepath tags — proven by
`OvergearedCompatTest.datapackTagAlsoTriggersTranslation` (a `somefuturemod`
result carrying `#lifepath:forged_outputs` translates with zero Java):

```jsonc
// data/lifepath/tags/item/forged_outputs.json
{ "id": "mymod:forged_ingot", "required": false }
```
