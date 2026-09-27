# M8-3 Skill expansion — justification gate record

Each candidate answered five questions: meaningful XP actions, grind
resistance, what progression improves, playstyle value, distinctness.
Verdicts: **ship** (implemented this milestone), **shipped** (already
exists), **reject**, **defer**.

## Woodcutting — SHIP

- XP: breaking non-player-placed `#minecraft:logs` blocks (mining
  activity + `required_tags`). Nether stems weighted up.
- Grind: `PlacedBlockTracker` already vetoes re-mining placed logs;
  diminishing returns bound repetition.
- Progression improves: level, aptitude floors, Lumberjack preset.
- Playstyle: forestry — the most common gathering loop in the game.
- Distinct: timber only; **foraging loses `#minecraft:logs`** so a log
  break feeds exactly one skill (plants/berries stay foraging).

## Hunting — SHIP

- XP: `combat` kill events whose victim carries `#lifepath:animals`.
- Grind: kill-based; bounded by mob density + diminishing returns.
- Progression: level, Hunter preset, floor.
- Playstyle: game-tracking/ranching loop.
- Distinct: animals only — hostile-mob kills stay athletics; both may
  fire on one event (parallel skills, by design).

## Engineering — SHIPPED (pre-existing)

- XP: all `crafting` events (base rate). Passed the gate in M2-4/#97.
- Backfilled this issue: milestone ladder + lang keys.

## Alchemy — DEFER

- XP loop exists (brewing), but vanilla brewing stands track **no
  owner** — there is no honest attribution for who inserted the
  ingredients. Wiring it needs a station-attribution primitive
  (last-interactor tracking on brewing stands + finish emit), which is
  a design item bigger than this issue's data+seam scope.
- Dependency: station-attribution primitive; revisit post-M8.
- Alchemist spec remains deferred for the same reason.

## Cooking — SHIP

- XP: `crafting` events whose output carries `#lifepath:cookable_foods`
  (bread, cake, stews, pies, …).
- Grind: crafting is ingredient-bounded; DR applies.
- Progression: Cook preset, floor.
- Playstyle: provisioning loop.
- Distinct: crafted foods only — smelted meats (furnace/smoker) are
  not covered (same attribution gap as alchemy; documented).
- Cook spec remapped farming→cooking this issue.

## Enchanting — REJECT

- Fails the frequency question: enchanting is an occasional,
  level-gated action — a skill fed by it would barely move. A
  passive-scaling or milestone-only knowledge path is the better fit
  later (and scholarship already covers the craftable knowledge loop).

## Melee — REJECT

- Not distinct enough: `combat` kill events already feed athletics;
  a melee-only duplicate would award the same kills twice with no new
  playstyle. Archery passes precisely because it splits on projectile
  kills; melee is the indistinguishable remainder of the same event.

## Archery — SHIP

- XP: new `lifepath:archery` activity — emitted alongside `combat` in
  the kill seam when `killedEntity.getRecentDamageSource()` is in
  `DamageTypeTags.IS_PROJECTILE`.
- Grind: ammo- and aim-bounded; DR applies.
- Progression: level; distinct ranged playstyle.
- Distinct: projectile kills only.

## Defence — SHIP

- XP: new `lifepath:defence` activity — `AFTER_DAMAGE` on players where
  `source.getAttacker()` is a living entity other than the player.
  Cause = `NON_PLAYER`; attacker type + entity tags feed per-subject
  and per-tag weighting (warden hits pay more than zombie hits).
- Grind: requires taking real hostile damage — self-inflicted and
  environmental damage are excluded at the emit seam.
- Distinct: survival-side activity, no overlap with kill events.

## Athletics — SHIPPED (M8-1)

- XP: `combat` kill events (per-subject/per-tag weighted).
- Backfilled this issue: milestone ladder + lang keys.

## Scholarship — SHIP

- XP: `crafting` events whose output carries `#lifepath:scholarly`
  (books, maps, bookshelves, lectern, cartography table, frames,
  spyglass, …).
- Grind: ingredient-bounded crafting.
- Playstyle: knowledge loop; unlocks the Scholar spec (the M8-2
  deferral resolves here — `specialization/scholar.json` shipped).
