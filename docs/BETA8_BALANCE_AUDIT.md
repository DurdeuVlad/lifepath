# Beta 8 — Balance Audit & Cross-Game Research (M18)

Single reference for every contested number in the Beta 8 milestone plan
(`BETA8_MILESTONE_PLAN.md`, decisions D1–D10). Status column legend:

- **implemented** — shipped in the working tree with the listed value.
- **proposed** — value is implemented as a provisional default but awaits team
  sign-off; the register below carries the rationale and alternative.
- **deferred** — not implementable as data today; parked with a reason.

Species ability counts, diets, and gating below were audited against the
shipped JSON under `data/lifepath/` — every claim maps to a file.

## 1. Kit audit matrix (M18-1)

| Species | Passives | Actives | Diet | Visibility | Strengths vs. weaknesses |
|---|---|---|---|---|---|
| human | 0 | 0 | — | open | True baseline — no kit, intended |
| sylvian | 6 | 1 | — | open | Forest affinity pack vs. arid intolerance |
| iceborn | 8 | 0 | — | open | Temperature-economy kit (richer than average by design); Frost Walk → frostbite (D1) |
| dwarf | 4 | 1 | — | open | Underground pack + compact frame (−15% scale) vs. sink-like-stone |
| dragonborn | 3 | 1 | — | open | Fire immunity (fixed seam) + molten regen vs. frost vulnerability |
| goliath | 3 | 1 | — | open | +25% scale frame (D7) vs. no stealth |
| amphibian | 3 | 1 | — | open | Water affinity pack |
| anima | 2 | 2 | herbivore | open | Morph kit vs. diet restriction |
| automaton | 3 | 2 | **ferrovore** (new) | open | Overclock + devour-iron (D6/D10) vs. rust in water *and* precipitation |
| enderian | 3 | 1 | — | open | Teleport kit vs. water burn incl. precipitation |
| hellborn | 3 | 1 | — | open | Nether Vigor repurposed → heat sources (D3) vs. aversion incl. precipitation + cold biomes |
| undead | 1 | 1 | necrophage | open | Kin disposition + death sight vs. **sun burn (new)** + hard diet |
| phantom | 3 | 1 | — | hidden/unlocked | Phase resource kit vs. sunflare while phased |
| celestial | 3 | 1 | — | hidden/unlocked | Unlockable tier |
| phoenix | 4 | 2 | — | hidden/unlocked | Fire kit + wings + death-molt vs. quench + rebirth frailty (D8) |

Findings from the audit:

- **Dead-content passives fixed:** `enderian_void_attunement` (End-gated on a
  server with no End) and `hellborn_nether_vigor` (Nether-gated) both existed
  as never-firing passives — both repurposed (D2/D3).
- **Asymmetry watch:** undead went from zero weather/sun cost to a burn
  passive — largest single-nerf in the batch; offset only by diet clarity
  (deny-feedback). Flagged for playtest.
- **Baseline parity:** every combat-relevant species now has at least one
  situational weakness *and* one identity strength; human remains the no-kit
  control.

## 2. Cross-game precedent research (M18-2)

| Mechanic | Precedent | What it does there | What we take from it |
|---|---|---|---|
| Water-walking removal | WoW Death Knight *Path of Frost* (cited in Discord); Genshin Kaeya ice-bridge | Temporary freeze-on-step with duration/cd; Kaeya's bridge still trivializes water | Team already ruled out placed blocks entirely — precedents confirm even *temporary* freeze trivializes boats. Frostbite-on-hit replaces traversal value with combat value |
| Freeze-on-hit | Origins-adjacent freeze powers; vanilla `powder snow` freeze ticks | Freeze ticks build → damage; readable on victim | `damage_dealt` → `freeze_ticks` on victim + internal cd (D1) |
| Sun damage | Origins Vampire / Vampirism mod | Burn in daylight under sky; shade/armor mitigate | `daylight + exposed_to_sky + !in_precipitation` → `ignite`; helmet exemption per D9 |
| Death-form reversion | Reincarnation Origins: Phoenix | Death → Rebirth (weakened) form → True form after a timer | `type:death` acquisition → staged `phoenix_rebirth` (frail stage → restored); re-death resets the molt |
| Flight costs | Origins **Elytrian**: free elytra, BUT armor capped at chainmail, +50% kinetic damage, claustrophobia debuff | Flight always carries a standing cost, never free | Phoenix flight gated on "flame whole" (molt grounds you); armor/kinetic costs flagged as D8 follow-up if true-form flight proves too strong |
| Fire-immunity symmetry | Origins **Blazeborn**: full fire immunity + on-fire damage bonus ↔ water/potion damage | Immunity is paired with a wet-cost | Dragonborn/phoenix fire immunity ↔ frost vulnerability / quench — already mirrored |
| Iron-eating | Origins dietary powers (carnivore/herbivore gates) | Diet is a *gate on nourishment*, not an item ban | Ferrovore diet = iron-only nourishment; Devour Iron is the active eat surface (D10) |
| Skill decay framing | Survival/RPG mods split real-time (e.g. skill rot in Project Zomboid) vs. play-time decay | Real-time decay must be *labelled* as real time to be fair | Decay text now says real-world hours explicitly (M16) |

## 3. Decision register — proposed resolutions (M18-3)

Values below are **implemented** in the working tree as provisional defaults;
each line is the proposal the team signs off or amends. Nothing here is
final until the M18-3 session.

| # | Decision | Proposed resolution | Status |
|---|---|---|---|
| D1 | Frost Walk replacement | `iceborn_frostbite`: `damage_dealt` → victim **160 freeze ticks** + Slowness I 100t + snowflake burst; internal cd **8s**. No placed blocks — per Pearman's call | implemented — numbers pending |
| D2 | `void_attunement` repurpose | "Depths sharpen you": below Y 0 **or** sky light ≤ 4 → Speed I + Strength I | implemented — pending |
| D3 | `nether_vigor` repurpose | "Hellfire vigor": near `#lifepath:heat_sources` or `on_fire` or `#lifepath:arid` biome → Strength + Speed | implemented — pending |
| D4 | `sturdy_frame` scope | All damage — shipped at **×0.75** (the existing multiplier was kept while scope widened). NOTE: above the ×0.9 proposal — 25% universal reduction on an open-tier species is strong; review in the decision session | implemented — pending, flagged |
| D5 | Denied-food UX | Hard-block the eat at the use-start gates (`ItemMixin` on `canEat` + `ItemUtilsMixin` on instant-use foods) so denied items are never consumed, plus an actionbar deny message; direct `Player.eat` calls keep the zero-nutrition backstop (matches Origins diet-gate feel without turning diets into item bans) | implemented — pending |
| D6 | Overclock duration | 160t → **480t (24s)**; cooldown unchanged 240s | implemented — pending |
| D7 | Scale deltas | Goliath +12% → **+25%**; Dwarf **−15%** (`dwarf_compact_frame`); other species unchanged | implemented — pending |
| D8 | Phoenix form semantics | Rebirth = `phoenix_rebirth` stage 0: frailty (weakness+slowness+scale −20%, grounded) 120s → restored stage 1 (full kit). Flight = creative-style `grant_flight` refreshed while flame is whole | implemented — pending; elytra-style glide rejected (more reach than creative flight per Elytrian precedent: glide is traversal, hover is identity) |
| D9 | Undead helmet exemption | **Rejected** — no armor-sense vocabulary exists; shade/shelter/precipitation mitigation already covers the vanilla rule's intent | implemented — pending |
| D10 | Iron-eating surface | Active ability `automaton_devour_iron` (consume held iron → `feed` action) — data-driven, no bespoke eat logic | implemented — pending |

### Residual balance concerns (carry into Beta 8 playtest)

1. **Iceborn is still the densest kit** (8 passives) — temperature economy
   needs an in-game feel check now that traversal is gone.
2. **Undead got a real cost** for the first time — sun burn + necrophage is
   the harshest open-tier kit; watch spawn-camp griefing.
3. **Phoenix flight is the same economy problem that killed Frost Walk** —
   grant is conditioned on molt state only; if travel trivializes, add an
   Elytrian-style cost (armor cap or kinetic damage) before unlocking.
4. **`damage_dealt` is new surface area** — any future on-hit ability shares
   the victim seam; watch for double-trigger on sweep attacks (covered by
   tests, but live-fire check recommended).
5. **Diet deny-message rate** — feedback fires per denied eat attempt; if it
   spams on hold-right-click, add a throttle in a follow-up.

---
*Audit inputs: shipped `data/lifepath/` JSON (Beta 7 + Beta 8 edits), Discord
Beta 7 thread, Origins docs (readthedocs), WoW Path of Frost, Genshin Kaeya,
Reincarnation Origins: Phoenix (CurseForge).*
