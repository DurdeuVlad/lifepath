# Anima morph — design + plan

Status: **shipped** (M-1…M-6, issues #159–#164 all merged).

## Shipped roster

Eight handpicked land animals under `data/lifepath/morph_form/` — stats
mirror the mob's own vanilla profile:

| Form | HP | Bite | Speed |
|---|---|---|---|
| fox | 10 | 3 | 0.30 |
| wolf | 8 | 4 | 0.30 |
| cat | 10 | 3 | 0.30 |
| rabbit | 3 | 2 | 0.35 |
| goat | 10 | 2 | 0.20 |
| panda | 20 | 6 | 0.15 |
| polar_bear | 30 | 6 | 0.25 |
| sheep | 8 | 2 | 0.23 |

## Intent

Anima species players can morph into one handpicked Minecraft animal —
chosen once at species-select, locked like the species itself. While
morphed the player *is* that animal: its hitbox, its HP, its speed, its
bite. No mining, no crafting, no station use. Morph back on command;
lethal damage forces the morph off instead of killing.

## Locked decisions (from the Discord discussion)

| Question | Decision |
|---|---|
| Forms per anima | **One**, picked at species-select, locked like species (singleplayer/op-2 exempt via the existing re-pick seam) |
| Roster | **Handpicked whitelist** — a `morph_form` content domain; land animals only |
| Damage carry | **Proportional** — morph HP is a % of the animal's max; demorph restores the same % of human max |
| Death while morphed | **Forced demorph, not death** — lethal hit un-morphs, the proportional damage carries over; if that still lands 0, death proceeds |
| Aquatic forms | **None.** Land animals only — dodges breath/water-logic entirely |
| 2×2 inventory crafting | **Blocked** while morphed (a fox can't fiddle with sticks) |

## Architecture — disguise, never possess

Every morph mod (Identity, Metamorph, the lot) keeps the player entity and
disguises it. Possessing a real mob fights inventory, chunk loading,
persistence, camera, and server authority — we do what they do.

| Layer | Mechanism | Reuses |
|---|---|---|
| State | `PlayerCharacterData.morph` = `{formId, active, lastMorphMs}` — persisted, synced via identity payload | existing sync funnel |
| Form content | `data/<ns>/morph_form/<name>.json`: `entity_type`, `display_name`, `description`, `icon`, `stats` (max_health/attack_damage/movement_speed/sprint?) | LifepathContent domain pattern |
| Selection | Anima species pick gains a form chooser step — same screen machinery as specialization pick, same lock semantics (server = permanent, SP/op2 = free) | `SelectionService`, `SelectionScreen` |
| Activation | `anima_morph` ACTIVE ability on the anima species; toggles morph, cooldown via `cooldown` field | `AbilityEngine`, `CooldownService` |
| Stats | morph applies/removes a `modify_attribute`-style bundle from the form's `stats` | `BuiltinActions` semantics, attribute registry |
| HP carry | morph: `hpRatio = currentHp / humanMax` → new hp = `ratio × morphMax`; demorph: inverse. Forced demorph on lethal damage; carry proportional; if carry ≥ max → real death | damage hook in `AbilityEngine` damage_taken path or a LivingEntity mixin |
| Client disguise | per-loader render hook: morphed player renders a cached entity of `form.entity_type` with pose/rotation copied; name tag optional (Identity hides it) | client mixin precedent |
| Hitbox | `Player.getDimensions`/`getEyeHeight` reads morph state → entity type's dimensions; `refreshDimensions` on morph/demorph | one mixin, both loaders share it via common |
| Restrictions | morphed ⇒ block-break speed zeroed (attribute), station-result extraction denied (`Slot.mayPickup`/`tryRemove` + the `clicked` funnel — covers 2×2, crafting tables, anvil/smithing, furnace, merchant, loom incl. double-click gather), block/item interaction cancelled | `SlotMorphPickupMixin`, `AbstractContainerMenuMorphMixin`, platform event seam |

## Out of scope (explicit non-goals)

- Aquatic / flying forms (dolphin, parrot-with-flight). Land animals only
  for v1; flying forms are a separate balance conversation.
- Animations beyond what the vanilla mob model does. A fox morph renders
  a fox — it walks, sits, bites like a fox. No custom rigs.
- Morph-time inventory access — the inventory stays full, the hands are
  paws. Taking items out mid-morph is a future conversation if testers ask.
- Other players morphing into *each other*'s forms, or mob-specific powers
  (fox pounce, parrot mimicry) beyond passive stats + melee bite.

## Issues

Filed: [#159](https://github.com/DurdeuVlad/lifepath/issues/159) [#160](https://github.com/DurdeuVlad/lifepath/issues/160) [#161](https://github.com/DurdeuVlad/lifepath/issues/161) [#162](https://github.com/DurdeuVlad/lifepath/issues/162) [#163](https://github.com/DurdeuVlad/lifepath/issues/163) [#164](https://github.com/DurdeuVlad/lifepath/issues/164).

- **M-1 Morph content domain + data model** — `morph_form/*.json` schema
  (`entity_type`, display fields, stat profile), `PlayerCharacterData.morph`
  field + codec + migration-safe defaults, identity-payload sync so clients
  can render disguises, whitelist validation.
- **M-2 Form selection UX** — anima species-select gains a second step
  (form picker reusing the specialization card grid), locked on servers,
  free re-pick in SP/op-2, admin override via `/lifepath`.
- **M-3 Morph activation + lifecycle** — `anima_morph` ability, toggle on/off,
  cooldown, stat profile apply/remove, HP proportional carry both ways,
  forced demorph on lethal damage, death edge (carry ≥ max → die anyway).
- **M-4 Client disguise rendering** — per-loader interception of the player
  renderer; morphed players render as their form entity (pose/rot copied);
  hitbox + eye-height swap; nametag policy (hidden while morphed).
- **M-5 Morph restrictions** — zeroed break speed, station-result
  extraction denial (crafting grids + every station output, including the
  PICKUP_ALL gather path), block/item interaction cancels; melee bite
  stays.
- **M-6 Roster + docs** — the handpicked form list with per-form stat
  tuning, `DATAPACK_API.md` + `ABILITIES.md` updates, anima species
  strings (`anima_morph` description EN+RO).

Dependency chain: M-1 → M-2/M-3 → M-4/M-5 → M-6. M-3 alone is testable
without M-4 (morph works, just looks like a crouching player).

## Risks / honest unknowns

- **Render disguise is the biggest lift** and the only part with no
  existing machinery — per-loader renderer interception has to handle the
  local player, remote players, first-person camera, and screen HUD.
- **Hitbox edge cases** — a fox at 0.6-wide mid-gap during demorph →
  suffocation rules apply (vanilla handles, but test).
- **Named-mob interactions** — a morphed fox near a hostile wolf mob…
  mob AI targets by entity type; morphed players are still `PlayerEntity`,
  so vanilla aggro doesn't change. Wolves won't mistake the fox for prey.
  (If testers want predators to react, that's a mob-AI mixin — out of v1.)
- **Forced-demorph loot edge** — morph death drops the human inventory
  normally; nothing extra to engineer.
