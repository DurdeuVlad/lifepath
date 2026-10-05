# Lifepath — Beta 8 Milestone Plan

> Source: Beta 7 field reports (17 screenshot reports, `Beta 7 bugs.zip`,
> 2026-10-04) plus the Frost Walk design discussion (Discord, 2026-10-04) and
> the Phoenix request (Reincarnation Origins: Phoenix reference,
> 2026-09-24).
> Status: **draft — ready for tracker creation.** Issue ids (M14-x …) are
> placeholders until GitHub numbers exist, same as `RELEASE_ISSUES.md` /
> `M13-MULTILOADER-PLAN.md`.
> Nothing here is committed to the tracker yet; this file is the handoff.

---

## Goal / System / Constraints / Evaluation

- **Goal:** ship a Beta 8 in which every reported Beta 7 defect is fixed or
  explicitly redesigned, Iceborn's water-walking is replaced by an approved
  buff, the dead dimension-gated passives are replaced, the skill UI explains
  itself, Phoenix becomes a real rebirth-capable species, and a balance audit
  produces signed-off tuning proposals.
- **System:** ability content is data-driven (`data/lifepath/ability/*.json`
  on the `trigger + conditions + target + actions` vocabulary); new mechanics
  extend `AbilityVocabulary` registrations — never species-specific Java.
  Client screens render only server-resolved strings.
- **Constraints:** no placed-block traversal aids on water (team decision —
  breaks boat/continent progression); no Nether/End-gated content may ship
  while the server has neither dimension; decay is wall-clock real time and
  must be presented as such; loader parity (Fabric + NeoForge) on every fix.
- **Evaluation:** each issue carries its own acceptance criteria and
  verification surface; in-game repro on a real server is the bar for every
  gameplay-facing item (dev-world-only proof was insufficient once already —
  see beta.7 morph hardening sweep).

## Decision register (needs owner sign-off before the affected issue starts)

Resolved entries record the shipped outcome inline.

| # | Decision | Proposal | Blocks |
|---|---|---|---|
| D1 | Iceborn replacement for Frost Walk | Passive frostbite-on-melee (`damage_dealt` → victim: freeze ticks + Slowness I). No block placement, per Pearman | M15-4, M14-3 |
| D2 | `void_attunement` repurpose (no End) | "Depths sharpen you": below Y 0 / in darkness → Speed + Strength | M15-6 |
| D3 | `nether_vigor` repurpose (no Nether) | "Hellfire vigor": near `#lifepath:heat_sources`, `on_fire`, or arid biomes → Strength + Speed | M15-7 |
| D4 | `sturdy_frame` scope | All-damage ×0.9 (matches its own description) vs. fall-only + text fix | M15-3 |
| D5 | Diet UX for denied food | **RESOLVED: hard-block.** `ItemMixin`/`ItemUtilsMixin` cancel the use before it starts (`canEat` wrap + `startUsingInstantly` gate); item is never consumed, `lifepath.diet.denied` actionbar explains it; `Player.eat` wrap kept as zero-nutrition backstop for direct `eat()` calls | M15-2, M15-5 |
| D6 | Overclock duration | 160t (8s) → ~400–600t (20–30s); keep 240s cooldown or rebalance both | M15-5 |
| D7 | Species scale deltas | Goliath ×1.12→~×1.25; add Dwarf ~×0.85; audit remaining species | M15-8 |
| D8 | Phoenix form semantics | **RESOLVED (docs/M17-PHOENIX-DESIGN.md):** real death → respawn in Rebirth Form (staged `phoenix_rebirth` condition, 120s → True Form marker stage); flight = refreshed `grant_flight` charged by `lifepath:flame` (−4/s while airborne vs. +2.5/s regen; grounded below 20); elytra-glide and cooldown-burst rejected | M17-* |
| D9 | Undead sun rule | Burn under daylight + sky access; helmet exemption (vanilla zombie rule) — yes/no | M15-2 |
| D10 | Automaton iron-eating surface | Active ability "Devour Iron" (consume_item + new `feed` action) vs. right-click item use | M15-5 |

---

## Milestone M14 — Ability vocabulary extensions

**Outcome:** the vocabulary can express precipitation exposure, sky exposure,
light level, outgoing damage, nutrition grants, and (if needed) nested
condition groups — the primitives every M15 fix composes.
**Why first:** four of the eight M15 issues are blocked on these.
**Risk:** `damage_dealt` is the only structurally new piece (new `Kind` +
victim binding); the rest are additive registrations following the M4-2/M4-3
contract.

### M14-1 — `in_precipitation` and `exposed_to_sky` / `light_level` conditions

- **Intent:** Beta 7 proves "inside water block" under-specifies wetness —
  rain and snow never touch it. Three species fixes need per-player
  precipitation exposure; undead sunburn and dwarf underground need sky/light
  probes.
- **Expectation:** ability JSON can gate on `{"type":"lifepath:in_precipitation",
  "state":"any|rain|snow"}` (sky-exposed player while the world rains/snows at
  their position), `{"type":"lifepath:exposed_to_sky"}` (`canSeeSky` at eye
  pos), and `{"type":"lifepath:light_level","source":"sky|block","op","value"}`.
- **Acceptance criteria:**
  - `in_precipitation` is true in rain *and* in snowfall (snowy biome), false
    under a roof, false on `ctx.self()==null` data paths (fail-closed).
  - `exposed_to_sky` false in caves and under a roof at noon.
  - `light_level` composes with `not`/`any` and honors `op`/`value`.
  - All three validate at datapack load (unknown ids fail the file, per the
    M4-2 contract).
- **Affected area:** `ability/BuiltinConditions.java`, `docs/DATAPACK_API.md`,
  `docs/ABILITIES.md`.
- **Non-goals:** no new weather simulation; no client-side prediction.
- **Verification:** unit tests on the data path where possible; in-game:
  `/weather set rain` + roof check + snowy-biome check.

### M14-2 — `feed`/`add_nutrition` action

- **Intent:** diet gating only *denies* nutrition; nothing can grant it.
  Automaton iron-eating needs a positive nutrition path.
- **Expectation:** `{"type":"lifepath:feed","nutrition":N,"saturation":S}`
  adds hunger/saturation to a player target; no-op on non-players.
- **Acceptance:** hunger/saturation land via `FoodData` (respects vanilla
  caps); no-op when `target.entity()` isn't a `ServerPlayer`.
- **Affected area:** `ability/BuiltinActions.java`, docs.
- **Verification:** automaton consumes an iron nugget via its ability and
  hunger visibly rises.

### M14-3 — `damage_dealt` trigger + `victim` target + `freeze_ticks` action

- **Intent:** the approved Iceborn direction ("frostbite when attacking") has
  no vocabulary — `damage_taken` only sees the *victim* and forbids actions.
- **Expectation:** a `damage_dealt` ability owned by the attacker evaluates
  after a hit lands; its actions run against a `lifepath:victim` target
  (the entity just damaged). Cooldown/cost apply normally (post-damage, no
  recursion risk unlike `damage_taken`). `freeze_ticks` sets
  `setTicksFrozen` on the target (+ optional slowness via `apply_effect`,
  which already works on `LivingEntity` victims).
- **Acceptance criteria:**
  - Melee hit by a frostbite owner applies freeze/slowness to the victim only.
  - Internal cooldown (existing `cooldown` field) throttles re-application.
  - The victim's own `damage_taken` modifiers still apply to the hit first.
  - Fail-closed: non-living victims (e.g., armor stands) don't crash the path.
- **Affected area:** `AbilityDefinition.Kind`, `AbilityEngine` (attacker-side
  dispatch mirroring `modifyIncomingDamage`), `BuiltinTargets`, `BuiltinActions`,
  `DamageInfo` (victim field), `VanillaGameplayProducers`/damage seam.
- **Non-goals:** no damage amplification of the hit itself in v1 (that's a
  multiplier question for the balance audit).
- **Verification:** unit test on the data path + in-game: zombie hit shows
  powder-snow freeze overlay ticks down.

### M14-4 — (conditional) nested `all`/`any` groups in conditions

- **Intent:** "A OR (B AND C)" is not expressible today (`all` is top-level
  AND, `any` is flat OR). The dwarf fix wants `cave_air OR (dark AND below
  sea level)`; phoenix stage kits may want the same.
- **Expectation:** a spec node like `{"type":"lifepath:all_of","conditions":
  [...]}` / `any_of` composable inside `all`/`any`, one level deep minimum.
- **Acceptance:** validator rejects cycles; `not` still composes;
  `unknownNodeTypes` covers nested nodes.
- **Scope note:** implement only if an M15 fix actually needs it — mark
  `needs-input` until then; a dedicated `underground` condition is the
  cheaper alternative.
- **Verification:** unit tests incl. a malformed nested node failing closed.

### M14-5 — (conditional) nested compound conditions — merged note

Deliberately kept inside M14-4: do not ship both a generic nesting node and a
pile of one-off conditions for the same need; pick per fix, record in the
issue.

### M14-6 — Sinking mechanic for Sink Like Stone

- **Intent:** the passive's description promises actual sinking; today it only
  slows.
- **Expectation:** a submerged dwarf is pulled down hard enough that surfacing
  takes effort.
- **Proposed approach (reversible):** prefer `modify_attribute` on
  `minecraft:generic.gravity` (1.21.1 attribute — zero new engine code) with
  `duration_ticks` tied to the passive interval. If gravity proves too weak
  in water, add a small `apply_motion`/`sink` action (bounded downward
  velocity while submerged).
- **Acceptance:** submerged dwarf sinks to the bottom without swimming input;
  surfacing is noticeably harder than vanilla; no drowning acceleration
  changes (air drains at vanilla rate unless separately decided).
- **Verification:** in-game water column test; check it doesn't break the
  morph hitbox interaction.

---

## Milestone M15 — Species kit correctness (Beta 7 defect batch)

**Outcome:** every reported species defect is fixed or deliberately redesigned;
no passive exists that can never fire on this server.
**Dependencies:** M14-1 (B: precipitation/sky/light), M14-3 (frostbite),
M14-2 + D10 (automaton diet), D1–D7 decisions.
**Risk:** dragonborn is a real engine/dispatch unknown — investigate first.

### M15-1 — Dragonborn fire immunity regression

- **Problem:** `dragonborn_fire_blood` declares `damage_taken` ×0.0 on
  `#minecraft:is_fire`, yet the reporter took fire damage *and* healed
  simultaneously (molten_core regen firing while burn ticks landed).
- **Intent:** the species fantasy ("flames cannot hurt you") must hold.
- **Investigation surface:** enable `debug_logging`, swim in lava and stand in
  fire; check whether `on_fire`/`lava` damage types actually match
  `#minecraft:is_fire` at the `actuallyHurt` seam, and whether any burn path
  bypasses `LivingEntity.hurt`.
- **Acceptance criteria:**
  - Standing in fire, burning ticks, and lava deal 0 damage to a dragonborn.
  - `molten_core` still grants regen/strength while on fire (it must not be
    "fixed" by disabling it).
  - Regression test on the `modifyIncomingDamage` data path for each fire
    damage type used by vanilla.
- **Non-goals:** hellborn's ×0.5 `infernal_heat` tuning (balance audit).
- **Verification:** in-game on both loaders; unit test per damage type.

### M15-2 — Undead: sunlight damage + diet clarity

- **Problem:** undead take no daylight damage (no such ability exists), and
  "can eat normal food" — the `necrophage` diet only zeroes nutrition, so the
  eat animation + consumption looks like eating works.
- **Expectation:**
  - New `undead_sun_burn` passive: `daylight` + `exposed_to_sky` (M14-1) →
    `ignite`/damage; helmet exemption per D9.
  - Diet decision per D5 — recommended: denied foods can't be consumed at all
    (cancel the use, show a denial line) so "eating" is unambiguous.
- **Acceptance:** undead burns in open daylight (not under a roof/at night);
  normal food visibly does nothing for them; `#lifepath:undead_foods` items
  still work.
- **Non-goals:** undead mob disposition tuning (already `undead_kin`).

### M15-3 — Dwarf pack (nightvision / sink / sturdy frame)

- **Subterranean fix:** trigger on underground semantics, not `cave_air` —
  mined tunnels are `minecraft:air`. Compose with M14-1 primitives
  (`light_level`/`exposed_to_sky` + optional `underground` or M14-4 nesting).
  Report wording: "underground or below a certain y axis".
- **Sink Like Stone:** real downward pull per M14-6.
- **Sturdy Frame:** either all-damage resistance matching its own description
  (proposal ×0.9, D4) or keep fall-only and fix the text — decision D4.
- **Acceptance:** night vision + haste apply in a player-dug tunnel and deep
  caves; dwarf demonstrably sinks; sturdy behavior matches its description
  verbatim.

### M15-4 — Iceborn: remove Frost Walk, add Frostbite (D1)

- **Intent (team decision, Discord 2026-10-04):** walking on water trivializes
  boats and island→continent travel — remove it entirely. Replacement is a
  combat buff, *not* placed blocks ("mai bine ceva buff").
- **Expectation:**
  - `iceborn_frost_walk` removed from `iceborn.json` actives and the file
    deleted/disabled; `freeze_water` action stays in the vocabulary (other
    uses may want it) but is unreferenced.
  - New passive `iceborn_frostbite`: melee hits apply freeze ticks + Slowness
    to the victim with an internal cooldown (numbers per D1).
  - Species description/strengths updated; EN/RO strings updated.
- **Acceptance:** an iceborn cannot cross water without a boat; melee hits
  visibly chill the target; no frosted-ice blocks are ever placed.
- **Dependencies:** M14-3, D1.

### M15-5 — Automaton pack (diet / rust / overclock)

- **Diet:** new `diet/automaton.json` (`allowed: #lifepath:automaton_foods` →
  iron nuggets/ingots) + the eat-iron surface per D10 (recommended: active
  ability "Devour Iron" = `consume_item` + `feed`, M14-2).
- **Rust in precipitation:** `automaton_rust` gains `any:` of `submerged` +
  `in_precipitation` (M14-1).
- **Overclock:** duration bump per D6 (proposal: 160→~400–600 ticks).
- **Acceptance:** normal food doesn't nourish (UX per D5); iron restores
  hunger; rust procs standing in rain; overclock buff length per decision.

### M15-6 — Enderian pack (rain burn / void attunement)

- `enderian_water_burn` gains `in_precipitation` (rain AND snow) via `any:`.
- `enderian_void_attunement` repurposed per D2 (proposal: below Y 0 or
  darkness → Speed + Strength); rename strings, update `enderian.json`
  description/strengths ("the End itself sharpens" is a dead promise).
- **Acceptance:** rain/snow hurts enderian; no End-gated passive remains;
  replacement is observable on this server.

### M15-7 — Hellborn pack (aversion / nether vigor)

- `hellborn_water_aversion` gains `in_precipitation` + `biome_tag
  #lifepath:cold` (tag already ships) via `any:`.
- `hellborn_nether_vigor` repurposed per D3 (proposal: near
  `#lifepath:heat_sources`, `on_fire`, or `#lifepath:arid` biomes → Strength +
  Speed); update `hellborn.json` strengths/description.
- **Acceptance:** weakness applies in rain, snow, and cold biomes; no
  Nether-gated passive remains; replacement observable.

### M15-8 — Species scale pass (D7)

- **Problem:** only `goliath_large_frame` touches `generic.scale` (+0.12 —
  visually negligible per the report).
- **Expectation:** visibly distinct heights per design sign-off; Goliath
  proposal ~×1.2–1.25, Dwarf ~×0.85; audit every species for intended stature
  (enderian tall?, automaton bulk?).
- **Acceptance:** each species' scale matches the signed-off table; hitbox and
  eye height follow `generic.scale`; first-person camera + morph dimensions
  interplay verified; tight spaces (1-block gaps for a small dwarf) are a
  documented consequence, not a surprise.
- **Non-goals:** no per-player scale sliders.

---

## Milestone M16 — Skill & milestone UX clarity

**Outcome:** a player can learn what a skill does, what the next milestone
concretely unlocks, and what "Protected for 30 hours" means — without Discord.
**Dependencies:** none (parallel to M14/M15; the only server change is an
additive payload field).
**Risk:** lowest of the three; mostly client screens + one codec bump.

### M16-1 — Skill row tooltips

- **Expectation:** hovering a skill row in `SkillsScreen` shows the skill's
  `description` — already shipped in `SkillCard.Display` and currently unused.
- **Acceptance:** every skill shows a factual one-line tooltip; absent
  descriptions degrade gracefully.

### M16-2 — Next-milestone unlock preview

- **Problem:** "Next milestone: Level 20: The water talks to you" is flavor
  text; the concrete effect (`skill_fishing_20` ability) exists server-side
  but isn't shipped.
- **Expectation:** `SkillCard.Details` gains `nextMilestoneEffects` — the
  milestone's `effects` refs resolved to `AbilityEntry`-style
  name/icon/description (same resolution as `bonuses`); `SkillDetailScreen`
  renders them under the milestone line (and/or as its hover tooltip).
- **Acceptance:** the player sees exactly what Level 20 grants, in plain
  language, before earning it; unresolved refs degrade to the flavor text.

### M16-3 — Decay phrasing: real time, stated

- **Expectation:** decay strings say *real-world* hours and note that offline
  time counts (decay is wall-clock `System.currentTimeMillis` — e.g.
  "Protected for 30 real hours (counts while you're away)"). EN + RO.
- **Acceptance:** no player can reasonably mistake decay for game-tick time;
  the grace/floor states read plainly.

### M16-4 — Skill icon audit + replacements

- **Expectation:** re-run the M12-4 icon audit on the 13 skills; replace the
  misleading ones flagged in the report; document icon sources in the audit.
- **Acceptance:** each icon reads as its skill at 16×16 (e.g., farming ≠
  abstract glyph); audit doc updated.

---

## Milestone M17 — Phoenix: rebirth & flight completion

**Outcome:** the hidden `phoenix` species becomes the reference design —
fire/gold kit plus a real death → Rebirth Form → True Form loop — rather than
a generic fire-resist kit.
**Context:** the reference mod (Reincarnation Origins: Phoenix) reverts the
player to a weakened "Rebirth Form" on death and restores the "True Form"
after a timer. Lifepath already owns most of the machinery: staged
`condition` defs with `advance_after_seconds`, a morph system, and a
lethal-damage seam (`PlayerEntityMixin.actuallyHurt` → `MorphService`
carries overflow onto the human bar).
**Current state:** `phoenix.json` (visibility `hidden`, selection
`unlocked`) + 5 abilities: `fire_blood` (fire immunity), `warmth` (heal while
burning), `quench` (water hurts), `rise` (300s self-heal+ignite), `flare`
(45s AoE ignite). **Missing:** death-triggered rebirth, flight, the two-form
identity.

### M17-1 — Phoenix design + reference research

- **Intent:** lock D8 before code: what Rebirth/True mean mechanically, and
  what "flight" is (glide vs. creative flight vs. temporary burst).
- **Expectation:** a short design doc/decision entry: form table (abilities,
  stats, restrictions per stage), rebirth trigger semantics (does death still
  happen → respawn as rebirth? or lethal hit converts to rebirth at 1 HP?),
  flight cost/limitations, unlock story (it's `selection:"unlocked"` — what
  unlocks it?).
- **Verification:** signed-off decision; no code.

### M17-2 — Death-triggered condition acquisition + `phoenix_rebirth`

- **Problem:** condition acquisition today fires on `type:attack` /
  `type:item` rules — nothing reacts to the character's own death/lethal hit.
- **Expectation:** a death/lethal seam acquires a staged `phoenix_rebirth`
  condition; stage 0 = Rebirth Form (weakened kit via `abilitiesAt(stage)`),
  `advance_after_seconds` → stage 1 = True Form (full kit back). Decide:
  lethal-hit conversion (no death screen) vs. post-respawn stage.
- **Acceptance:** dying as phoenix visibly enters Rebirth (abilities swap,
  HUD/strings show it); True Form returns on the timer; normal species have
  no path to the condition.
- **Dependencies:** M17-1 (semantics), D8.

### M17-3 — Phoenix flight

- **Problem:** no flight vocabulary exists (no `mayfly`-style action/condition
  path).
- **Expectation:** per D8 — e.g. a `set_flight`-style action or
  attribute-driven glide gated on stage/conditions, with a resource or
  cooldown cost so it isn't free travel (same lesson as Frost Walk).
- **Acceptance:** flight works as decided, costs something, is disabled in
  Rebirth Form, and can't be triggered by non-phoenix characters.
- **Dependencies:** M17-1, D8; research from M18-2 informs the cost model.

---

## Milestone M18 — Balance audit & cross-game research

**Outcome:** an evidence-backed balance baseline + proposals the team signs
off on — *before* tuning numbers spread across datapack files.
**Why separate:** tuning decisions (D-column items, Overclock, Sturdy Frame,
scale deltas, frostbite numbers, phoenix costs) belong to one audited table,
not per-issue guesses.
**Independence:** can run in parallel with everything; its outputs amend
M15/M17 acceptance numbers.

### M18-1 — Internal kit audit matrix

- **Expectation:** a `docs/` audit table covering all 15 species: passive/
  active counts, strength-vs-weakness symmetry, dead-content passives
  (dimension-gated, zero-effect), capacity multipliers, aptitudes, diets —
  each cell pointing at the shipped JSON.
- **Acceptance:** every species row complete; every "useless on this server"
  passive either has an M15 fix or a documented removal; gaps list is empty.

### M18-2 — Cross-game precedent research

- **Expectation:** a precedent table for each contested mechanic — water-
  walking removal (WoW Path of Frost was the cited precedent), freeze-on-hit,
  sun damage (vampires in Origins/Vampirism), death-form reversion
  (Reincarnation Origins Phoenix), flight costs (Origins Elytrian), iron-
  eating (Origins dietary powers), skill decay framing (real vs game time in
  survival/RPG mods) — source links + what each does + cost/balance model.
- **Acceptance:** every D-decision has at least one cited precedent; research
  notes land in `docs/`; no mechanic ships on "feels right" alone.

### M18-3 — Balance proposal pack + decision session

- **Expectation:** the audit + precedents distilled into per-species proposed
  numbers (overclock duration, sturdy multiplier, scale deltas, frostbite
  ticks/cd, phoenix costs, sunburn rate) for team sign-off; approved numbers
  patch the M15/M17 acceptance criteria.
- **Acceptance:** every `needs-input` decision D1–D10 resolves to "approved
  value" or "rejected with reason"; the audit doc is the single reference.

---

## Dependency map

```
M14 (vocabulary) ─┬─> M15-2,3,5,6,7 (species fixes needing new primitives)
                  └─> M15-4 (frostbite)  [+ D1]
M15-1 (dragonborn) is standalone — investigate first, it's a live regression.
M16 (UX) is standalone — parallel-safe.
M17 (phoenix) ── depends on M17-1 design + M18-2 precedent for flight cost.
M18 (audit) ─── independent; its outputs amend M15/M17 numbers.
```

## Suggested order

1. M15-1 (dragonborn — live regression, cheap).
2. M14-1 + M14-2 (unblocks most species data work), M14-3 (frostbite seam).
3. M15 data sweep (3–8) once primitives land; D-decisions get resolved as
   they come up or batched into the M18-3 session.
4. M16 anytime in parallel.
5. M17 after design sign-off.
6. M18 runs alongside, gates nothing but informs all numbers.

## PR contract reminder

Every PR that lands an issue above repeats in its body: intent, expectation,
acceptance criteria with evidence, non-code context, scope/non-goals,
verification + risk. `None` / `Not applicable` / `Unknown—blocked` explicitly
where it applies — never leave a reviewer guessing.

## Residual risks / honest flags

- **Dragonborn:** the ×0 `is_fire` negation *looks* correct on paper — if the
  repro shows burn ticks bypassing `hurt`/`actuallyHurt`, the fix is a mixin
  seam question, not data. Treat M15-1 as an investigation issue.
- **Undead eating:** the report may be describing working-as-designed
  zero-nutrition consumption rather than a broken gate — verify in-game before
  building the hard-block (D5 covers both outcomes).
- **`any`/`all` composition limit:** A OR (B AND C) isn't expressible today;
  M14-4 exists only if a fix needs it — don't build it speculatively.
- **Frost Walk removal** is a player-visible kit nerf — pair it with the
  frostbite buff in the same release so Iceborn doesn't land strictly weaker.
- **Phoenix flight** is the same travel-economy problem that killed Frost
  Walk — cost/limitations are a design requirement, not polish.
