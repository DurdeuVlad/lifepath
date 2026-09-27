# M13-6 evidence — Overgeared × Lifepath live co-load (issue #138 / #61)

Test instance: NeoForge **21.1.252** dev server + dev client, Minecraft 1.21.1.

| Mod | Version |
|---|---|
| Lifepath | `1.0.0-beta.1` (`lifepath-1.0.0-beta.1-neoforge.jar` wiring) |
| Overgeared: A Blacksmith Mod | `1.21.1-1.6.19` (`overgeared-neoforge` jar from Modrinth) |
| Sable Companion | `1.6.0` (dependency auto-bundled by Overgeared) |

Note: `gradle.properties` pins NeoForge `21.1.252` for the dev env because
`overgeared-neoforge-1.21.1-1.6.19` requires `neoforge [21.1.227,)`. The shipped
`neoforge.mods.toml` range `[21.1,)` is unchanged.

## Check 1 — both installed, forging loop intact

Server mod list at boot:

```text
Lifepath 1.0.0-beta.1 (lifepath)
Minecraft 1.21.1 (minecraft)
NeoForge 21.1.252 (neoforge)
Overgeared: A Blacksmith Mod 1.21.1-1.6.19 (overgeared)
Sable Companion 1.6.0 (sablecompanion)
```

Lifepath init on the same boot:

```text
[modloading-worker-0/INFO] Lifepath initializing (version 1.0.0-beta.1, data version 2)
[modloading-worker-0/INFO] Overgeared forging → Smithing XP bridge active
[modloading-worker-0/INFO] External activity adapter registered: lifepath:overgeared
```

Server reached `Done (2.032s)`. The Overgeared recipe was executed by vanilla
crafting mechanics — Overgeared's own content, not replaced or bypassed:

- `crafting-recipe-filled.png` — recipe book auto-filled
  `overgeared:alloy_furnace` (8 bricks + blast furnace ring), "Alloy Furnace"
  tooltip, result slot populated.
- `crafting-result-taken.png` — crafting grid consumed after taking the result.
- `in-world-alloy-furnace.png` — player holding the crafted alloy furnace
  beside the crafting table.

Server-side NBT after the craft:

```text
$ data get entity Dev Inventory
Dev has the following entity data: [{count: 1, Slot: 9b, id: "overgeared:alloy_furnace"}]
```

## Check 2 — Overgeared activity → Lifepath Smithing progression

Baseline before the craft:

```text
$ lifepath debug skill Dev lifepath:smithing
Lifepath debug skill lifepath:smithing for Dev
  <no progress recorded>
  diminishing returns: <no in-window hits>
```

Immediately after crafting `overgeared:alloy_furnace`:

```text
$ lifepath debug skill Dev lifepath:smithing
Lifepath debug skill lifepath:smithing for Dev
  level=0  xp=0.5  highest=0  floor=0  aptitude=B  effective_aptitude=B
  last_use=2026-09-27T21:56:07.074Z  checkpoint=2026-09-27T21:56:07.074Z
  decay projection: frac=0.01 (floor 0)
  diminishing: lifepath:smithing|overgeared:alloy_furnace count=1 mult=1.0
```

The diminishing-returns key `lifepath:smithing|overgeared:alloy_furnace` is the
adapter's fingerprint: the generic crafting producer emitted the activity keyed
to the `overgeared:*` output, `OvergearedCompat` republished it under the
canonical `lifepath:smithing` skill, and the XP engine consumed it. No
Overgeared class is referenced by Lifepath — the bridge is entirely the
datapack-tag/`overgeared:*`-namespace declarative slice.

## Check 3 — Overgeared absent → graceful

Same server binary, `run/mods/overgeared-*.jar` moved aside:

```text
[WARN] The following mods have version differences that were not resolved:
overgeared (version 1.21.1-1.6.19 -> MISSING)
sablecompanion (version 1.6.0 -> MISSING)
[INFO] External activity adapter registered: lifepath:overgeared
[INFO] Done (2.376s)! For help, type "help"
```

The adapter still registers (ServiceLoader seam) but its `isModLoaded` gate
skips wiring — the `Overgeared forging → Smithing XP bridge active` line is
absent. `MISSING` warning is NeoForge's world-recorded-mod notice, not an
error. Loaded 1290 recipes, full Lifepath datapack reload clean.

## Check 4 — public messaging

README, `docs/COMPAT.md`, and `docs/CURSEFORGE.md` updated in the same commit:
the adapter is **validated on NeoForge 1.21.1** (this evidence set) and remains
**dormant on Fabric** because Overgeared ships no Fabric 1.21.1 build — the copy
states both facts explicitly.
