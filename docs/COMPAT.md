# Compatibility model (M7-1)

Lifepath is a progression layer over other mods' gameplay. External systems
provide content; Lifepath provides XP/levels/aptitude/abilities on top. Nothing
here replaces a foreign loop.

## Loader support (M13)

| Loader | Minecraft | Status | Artifact |
|---|---|---|---|
| **NeoForge** 21.1+ | 1.21.1 | **Primary** — full feature parity | `lifepath-<ver>-neoforge.jar` |
| **Fabric** 0.16.10+ (+ Fabric API 0.116.0+) | 1.21.1 | Secondary — full feature parity | `lifepath-<ver>-fabric.jar` |

One shared `common/` codebase (Mojang mappings, zero loader imports —
CI-enforced) sits behind `platform/` ports; each loader module is a thin
adapter. Save-format (`lifepath:character_data` NBT) and datapack content
are identical on both. Mod-to-mod bridges plug in through the
`lifepath:adapter` seam — a Fabric entrypoint on Fabric, a
`ServiceLoader` service on NeoForge.

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

Implement `com.dwurdy.lifepath.compat.ExternalActivityAdapter`.
Discovery is per-entrypoint `Throwable`-isolated — a bridge built against a
different version logs an error and is skipped, never crashes init.

## Overgeared adapter (`compat/overgeared/`)

Overgeared ships **no Fabric build for 1.21.1** (1.20.1 only; 1.21.1 is
NeoForge) and exposes no stable event API — so the shipped adapter is
deliberately declarative. **Validated live on NeoForge 1.21.1**
(`overgeared-neoforge-1.21.1-1.6.19` + `lifepath-1.0.0-beta.1-neoforge`,
NeoForge 21.1.252): co-load is clean, the adapter activates via `ModList`
detection, and crafting `overgeared:alloy_furnace` produced Smithing
progression — evidence in `docs/evidence/m13-6/` (issue #138). On Fabric the
adapter stays dormant:

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
| Overgeared absent | `register()` logs one debug line, wires nothing; vanilla paths untouched (boot-verified on both loaders) |
| Overgeared present (NeoForge) | dispatcher subscription active; `overgeared:alloy_furnace` craft → `lifepath:smithing` XP observed live (`docs/evidence/m13-6/`, issue #138) |
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

## Material-gate leak surface (M26, issue #223)

`material_gates` on an outcome rule deny producing tier-tagged outputs
below a skill level. The gate is enforced at result-computation time on every
production seam, not just the Overgeared anvil:

| Path | Seam | Blueprint bypass |
|---|---|---|
| Overgeared smithing anvil | `AbstractSmithingAnvilBlockEntity.craftItem`/`craftItemWithBlueprint` (HEAD, `cancellable`) | Yes — non-empty blueprint slot licenses the craft |
| Overgeared drafting table | `BlueprintWorkbenchMenu.createBlueprint` (HEAD) — `blueprint_min_level` gates blueprint creation itself | n/a |
| Vanilla crafting table + inventory 2x2 | `CraftingMenu.slotChangedCraftingGrid` (TAIL clears result) — covers click, shift-click, and pick-all | No — no blueprint slot exists |
| Vanilla smithing table | `SmithingMenu.createResult` (TAIL clears result) — gates `smithing_transform` and trims producing tagged gear | No |

Shared check: `com.dwurdy.lifepath.skill.MaterialGates.denialFor(player, stack, licensed)`.
If the anvil's forging-session owner is offline when the craft completes,
gated output fails closed — the level check can't run, so the craft is
cancelled rather than produced unlicensed (`MaterialGates.isGatedOutput`).
When a forged result merges into a matching stack already in the output
slot, outcome scaling applies only to the crafted delta, not the whole
stack.

Live-verified on the NeoForge dev server (Overgeared 1.6.19 + Kaleidoscope
Cookery 1.5.1 co-loaded):

- Crafting grid, `flint_and_steel` (tier_iron): output empty at Smithing 0,
  crafted at 10.
- Smithing table, iron-chestplate trim (tier_iron): denied at 0 with the
  "Beyond your craft" actionbar; netherite upgrade (tier_netherite) denied at
  10, produced at 70.
- Anvil, `steel_boots` (tier_steel): denied at 0 (inputs unconsumed), produced
  at 30; `steel_sword_blade` produced at 0 with a sword blueprint.
- Drafting table: empty blueprint consumed → output blueprint at Smithing 20;
  nothing happens at 0.

Remaining intentional non-goals: loot chests, mob drops, villager trades,
and item repair/acquisition (anvil, grindstone) are *not* crafting — they are
not gated. Netherite is gated at 70 via `smithing_tier_netherite` — the tag
existed but was missing from `material_gates`, which let anyone upgrade
straight past the ladder.

Known residual leak: the vanilla **Crafter** block produces recipe results
with no player involved, so it bypasses every gate. On NeoForge+Overgeared
this is mostly moot (vanilla gear recipes are wiped to air), but on Fabric a
Crafter can mass-produce tier-tagged gear such as iron tools. Closing it
requires a design decision — whether automated crafting should check the
*owner* of the placer — and is deliberately out of scope for M26.
