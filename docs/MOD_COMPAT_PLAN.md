# Rustic Craft 2 pack compatibility — milestone plan

> Status: **accepted** — issues filed on GitHub, sequencing set.
> Source of truth for the pack: `rusticcraft2-clona/new-clone-28-sept/_/mods` (268 jars).

## Locked decisions (2026-09-30)

- **Vampirism/Werewolves overlap → integrate, don't hide.** Lifepath keeps
  its own vampire/lycanthropy implementation as the no-mod fallback. When
  the mods are present, a bridge defers to their state and our duplicate
  mechanics shut off. Bridge errors are marked and the bridge auto-cuts —
  never silent, never a crash. (Replaces the A8 needs-input.)
- **Cooking XP → the player who takes the result** (A4).
- **Stardew fishing → audit first** (A7).
- **Pack-specific tuning lives on the RC2 server datapack; generic compat
  ships in the jar** — e.g. hiding a species for RC2 would be datapack-side.
- **beta.6 ships after skills payoff lands** — one cut carries the merged
  fixes, this compat milestone, and skills payoff (tracked separately).

## Contract

- **Goal**: Lifepath reacts meaningfully to the RC2 pack — skills, diet,
  encumbrance, and species all *notice* Overgeared, Farmer's Delight, Create,
  and the rest of the list — without hard dependencies and without
  automation-XP exploits.
- **How compat works here (already built)**: three escalating seams —
  1. **data**: `item_weight` tables, `xp_source` activity maps, `lifepath:*`
     tags, diet definitions — zero code, ships in the jar;
  2. **adapter**: `ExternalActivityAdapter` + `lifepath:adapter` entrypoint /
     `META-INF/services` — translates foreign events into normalized
     `ActivityEvent`s, no foreign classes ever loaded (`compat/` package);
  3. **engine**: new SpecNode primitives / soft-dep hooks — only when data and
     adapters can't reach the surface.
- **Constraint**: generic services must never name a specific mod — the
  grep-clean boundary in `compat/` is load-bearing.

## Already true today (evidence)

- `compat/overgeared/OvergearedCompat` republishes crafting events whose
  output is `overgeared:*` or `#lifepath:forged_outputs` as `smithing`
  activity — registered via `META-INF/services` + `fabric.mod.json`
  entrypoint. Whether Overgeared 1.6.17 forging actually produces a crafting
  event is **unverified** (see issue 3).
- `smithing_workstations`/`forged_outputs` tags already carry optional
  `overgeared:` entries.
- `player_caused_only` exists but `onBlockBreak` only checks
  `instanceof ServerPlayer` — Create `DeployerFakePlayer` **passes that
  check** (issue 4).
- Supplementaries Sack + Backpacked backpacks store contents in vanilla
  `DataComponents.CONTAINER` — already covered by encumbrance recursion
  (#146/#147).

## Milestone A — "The pack feels like it was designed for Lifepath"

**Outcome**: on RC2, a new player's pack content just works — meals give
Cooking XP, forging gives Smithing XP, FD crops give Farming XP, Create
machines can't farm XP, and species identity extends into mod content.

**Proof**: each issue's acceptance criteria verified on the actual
`rusticcraft2-clona` server or an equivalent NeoForge 21.1 instance with the
named mods loaded.

---

### Issue A1 — Data coverage: tags, weights, XP maps for the pack

**Intent**: the zero-code layer. Every gameplay-relevant item/block the pack
adds should map into an existing Lifepath surface.

**Scope — in**:
- `item_weight`: `#c:ingots/steel` done; audit Create blocks (cogwheel/shaft/
  andesite alloy — machinery is dense, ~2–4), FD storage blocks (crates/bags),
  Overgeared casts/anvils, Supplementaries bricks.
- `xp_source` data: `cooking` subjects for `farmersdelight:*` meals +
  cluster (`brewinandchewin`, `farmersrespite`, `culturaldelights`,
  `romaniansdelight`, `cookscollection`, `kaleidoscopecookery`,
  `hearthandharvest`, `letsdo:vinery`, `large_meals`, `fungidelight`);
  `smithing` subjects for `overgeared:*` outputs; `engineering` subjects for
  `create:*` components.
- Tags: `harvestable`/`click_harvest`/`growable` ← FD crops (rice, onions,
  tomatoes, cabbage); `cookable_foods` ← cluster meals; `animals`/
  `undead`/`bloodsuckers` ← MowziesMobs, Naturalist, Hybrid Birds,
  realmrpg_skeletons, Ribbits.
- Diet files: map FD cluster meals into `blood`/`herbivore`/`necrophage`
  categories so species diets apply to modded food.

**Acceptance**: validator reports zero unresolved pack ids in the shipped
data; breaking a mature FD rice crop yields farming XP; listed ids resolve
under the real pack.

**Non-goals**: no new engine surfaces; no adapter code.

### Issue A2 — Automation integrity: FakePlayer events can't farm XP

**Intent**: Create deployers/contraption drills must not level a player's
skills — or waste scans on a fake player's character file.

**Expectation**: events whose effective player is a `FakePlayer` (or
NeoForge's `FakePlayerFactory` products) publish with `Cause.NON_PLAYER` or
are dropped; `player_caused_only` xp_sources then never fire for machines.

**Scope**: `VanillaGameplayProducers` + `ActivityEvent.Cause` attribution;
audit combat/archery/defence producers for the same hole.

**Acceptance**: a Create mechanical drill breaking `lifepath:minable` blocks
grants no XP to any real player; deployer-placed FakePlayer events never
reach `Cause.PLAYER`; test coverage at the dispatcher level.

### Issue A3 — Verify + deepen Overgeared on NeoForge

**Intent**: the existing adapter assumes forging surfaces as crafting events
— on RC2's Overgeared 1.6.17 this is a guess. Prove or fix it.

**Expectation**: forging a steel tool on an Overgeared anvil yields
`lifepath:smithing` XP. If Overgeared's result slot bypasses the vanilla
craft path, the adapter needs a targeted hook (mixin to its result slot, or
its own event — whichever is observable without hard-dep).

**Acceptance**: scripted or manual test on the clone pack shows XP on
forge; `ATTR` marker (`compat=overgeared`) visible in debug logs; quality
outputs (Overgeared quality system) award differentiated XP via
`per_subject`/tags.

### Issue A4 — Farmer's Delight cooking stations → Cooking XP

**Intent**: pot/skillet/cutting-board cooking is the pack's main cooking
loop; vanilla craft events don't see block-entity output slots.

**Expectation**: taking a result from a `farmersdelight:cooking_pot` /
skillet / cutting board emits `lifepath:crafting` (or a dedicated
`lifepath:cooking` activity) attributed to the player who placed the pot.

**Risks**: station output slots aren't vanilla `ResultSlot` — likely a
mixin into FD's container, gated behind mod presence. Attribution when
player A places the pot but player B takes the food — decide: taker gets XP
(simple) vs owner (needs tracking). **needs-input** on that choice.

### Issue A5 — Species physicality via Pehkui (soft-dep)

**Intent**: the pack ships Pehkui; Dwarf/Goliath should *be* their size, not
just claim it in flavor text.

**Expectation**: optional `scale` field on species; when Pehkui is present,
apply `pehkui:base` scale on identity set (Goliath ~1.2, Dwarf ~0.8 …).
Absent Pehkui → field ignored, no crash, no class loading.

**Non-goals**: hitbox/gameplay-affecting scale beyond Pehkui's own
mechanics; no scale for specs.

### Issue A6 — Serene Seasons condition primitive

**Intent**: seasons are the pack's biggest ambient system; species fantasy
demands seasonal reactions.

**Expectation**: new SpecNode condition `lifepath:season` (params: `seasons`
list) usable in abilities/conditions; reads SereneSeasons when present,
always-false (with one-time warn) when absent. Enables: Iceborn abilities
stronger in winter, Amphibian `dry_skin` harsher in summer, Sylvian bloom in
spring — authored as data, not code.

**Dependencies**: none, but pairs naturally with new content abilities.

### Issue A7 — Pack-path audit: fishing + harvest mods

**Intent**: `stardew_fishing` replaces the fishing minigame and
`rightclickharvest` harvests without breaking blocks — both may bypass our
vanilla producers silently.

**Expectation**: document which emits events we see; where silent, decide
adapter vs accept-gap per system. **needs-input**: is Stardew fishing worth
an adapter, or is vanilla-rod fishing a niche path in this pack?

### Issue A8 — Vampirism/Werewolves bridge (decided: integrate)

**Intent**: the pack runs Vampirism + Werewolves; Lifepath ships
`vampirism`/`lycanthropy` *species*. Decision: **integrate** — our species
stay pickable (ancestry/identity), but when the mods are present a bridge
defers mechanical state to them so sun-burn/night-vision/blood effects
don't double-apply.

**Expectation**: soft-dep condition primitives (`lifepath:mod_vampire`,
`lifepath:mod_werewolf`) read the mods' state when installed and return
false when absent — no foreign class loading without the mod. Our
`vampirism_*`/`lycanthropy_*` abilities gain a `NOT bridge_active` condition
so theirs win when bridged. Bridge errors are marked and counted; after a
threshold the bridge cuts for the session with a loud log — resilience, not
crashes.

**Acceptance**: with Vampirism installed, a `vampirism` species player's
sun-damage comes from the mod, not our ability; mod absent → our
implementation works as today; induced error → logged + bridge cut, game
continues.

### Issue A9 — Skills payoff: milestone ability grants (decided: full trees)

**Intent**: testers report skills are "de afișaj" — XP/levels/milestones
work but grant nothing mechanically. The schema already supports
`milestone.effects` → ability ids and `SkillSummary` displays them as
bonuses; nothing grants them.

**Expectation**: a `LEVEL_UP` listener grants the milestone's `effectRefs`
to the character's owned abilities; abilities persist, surface in
IdentitySummary (Character screen/HUD), and are revoked if decay drops the
level below the milestone. Requires an owned-abilities set on
`PlayerCharacterData` + persistence.

**Content**: all 13 skills × shipped milestones (typically 20/40/60/80/100)
get real rewards — ~60 authored ability defs reusing existing primitives,
EN+RO keys + descriptions. `deepvein_sense_i`, `forge_mastery_i`,
`bountiful_harvest_i`, `patient_waters_i` exist as the tier-1 pattern.

### Issue A10 — beta.6 release tracking

Rollup: merged fixes (selection lock, mandatory pick, ability tooltips,
encumbrance containers/tuning/leeway) + A1–A4 + A8 + A9. Release notes call
out `encumbrance.toml` regen and the vampire bridge. Gates: build workflow
green, NeoForge jar has no bundled night-config, loader metadata parity.

## Dependency order

A2 (integrity) → A1 (data) → A3/A4 (adapters, parallel) → A9 (skills
payoff) → A8 (vampire bridge) → A10 (release). A5/A6/A7 follow in the
post-beta.6 flavor pass.

## Residual risks

- Overgeared forging may be unobservable without a hard dep (A3 fallback:
  document gap).
- FD container internals may shift between 1.3.x versions (A4 tests pin the
  pack's version).
- FakePlayer detection differs Fabric↔NeoForge (A2 must cover both loaders).
