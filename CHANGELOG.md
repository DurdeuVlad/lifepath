# Changelog

All notable changes to Lifepath, newest first. Each release ships two jars on
GitHub Releases: `-neoforge` for NeoForge 21.1.x and `-fabric` for Fabric
0.16.x, both on Minecraft 1.21.1. SHA-256 checksums are attached to each
release.

Report bugs: <https://github.com/DurdeuVlad/lifepath/issues> — use the "Bug
report" template, include loader, version, and repro steps.

## [Unreleased]

### Changed — anti-one-man-army economy pass

- **Breadth tax on skill decay** (`decay.toml`: `breadth_enabled`,
  `breadth_threshold` 25, `breadth_per_skill` 0.15, `breadth_cap` 1.5) —
  each *non-specialization* skill at or above the threshold inflates this
  skill's decay rate by 15%, capped at +150%. A player holding six
  unrelated skills at high level pays real upkeep; a specialist's own
  skills are exempt — the tax punishes breadth, not depth.
- **Sharper low-skill outcome bands** across all crafting/gathering
  skills: untrained pays 1.75× anvil cost with 30% botch chance and 0.35×
  output (smithing), novice 1.4× / 13% / 0.5×. Experts keep their edge —
  master/legendary cost multipliers improve (0.4× smithing) and master
  yields rise. Doing the work without the skill now *costs* you.
- **Quality gets real teeth via deps.** Stamped `lifepath:quality` tiers
  are now also written onto the owning mod's quality component
  reflectively: Overgeared `overgeared:forging_quality`
  (crude→POOR … masterwork→MASTER — real durability/speed multipliers)
  and Kaleidoscope Cookery `kaleidoscope_cookery:quality`
  (crude→POOR … masterwork→SUPERB — real food-effect scaling). Absent
  mods degrade to the lore stamp as before; no compile-time dep.
- **Material gates on the Overgeared anvil** (NeoForge): smithing
  `outcome_rule` gains `material_gates` — iron-tier output needs
  Smithing 10, steel 30, diamond 50. A **blueprint in the anvil's
  blueprint slot bypasses the level check** — blueprints become the
  tradeable chokepoint: specialists draft, customers forge.
- **Blueprint drafting is skilled work.** `blueprint_min_level: 20` on the
  smithing rule — below it, the Blueprint Workbench refuses the draft,
  so non-smiths can't self-supply the bypass.

### Fixed — live-verification follow-ups

- **Vanilla leak closure.** Material gates now also bind the vanilla
  crafting grid (`CraftingMenu.slotChangedCraftingGrid` clears denied
  results — covers click, shift-click, and pick-all) and the vanilla
  smithing table (`SmithingMenu.createResult` clears denied transform/trim
  results). Netherite joins the ladder at `smithing_tier_netherite`: 70.
- **Blueprint workbench gate actually fires.** The menu keeps no player
  reference — the check now resolves the drafter through the menu's slots
  (previously it silently found nothing and never denied).
- **Anvil output seam.** Overgeared 1.6.19 writes forged results into the
  anvil's output slot instead of dropping them — outcome scaling, quality
  stamping, and the `forging_quality` bias now inspect that slot too.
- **Denial no longer NPEs** for players with no recorded progress on the
  gated skill (treated as level 0).
- **Anvil gate fails closed.** A forge completing while its session owner
  is offline cancels gated output instead of producing it unlicensed;
  results merged into an existing output-slot stack are scaled by their
  crafted delta, not the whole stack. Deny messaging is rate-limited
  per-player instead of globally.
- **Tier tags now cover Overgeared intermediate parts** (blades, heads,
  plates) so blueprint-licensed recipes are actually gated.
- `debug open <player> <pos>` opens any container GUI (block-entity menus
  plus `BlockState.getMenuProvider` blocks like the drafting table) and
  `debug forge <pos>` fires the anvil's real craft path — admin tooling for
  autonomous verification.

### Added

- **Brewer specialization + Brewing skill** — potion take-outs from a
  brewing stand pay Brewing XP weighted by potion strength (splash >
  drinkable < lingering; strong/extended variants pay more, mundane /
  water pay scraps). Brewer starts Brewing 20 / Scholarship 15 /
  Foraging 10, aptitude A on brewing, brewing floor 30, +35% brewing XP.
  Brewing outcomes scale like other crafting skills — crude early vials,
  masterwork elixirs.

### Changed

- **Level curve is now linear.** Each level costs `40 + 5·level` XP (was a
  growing ~8%/level ramp): level 20→21 drops from 325 → 145 XP, total to
  100 drops ~78k → 29k. Early levels are cheap, late levels still climb.
- **Smithing XP covers all Overgeared materials.** New tier tags
  `smithing_tier_steel` (3.0 — `overgeared:steel_*` gear, steel arrows,
  `#c:forged/steel`), `smithing_tier_copper` (1.0), `smithing_tier_stone`
  (0.75); `smithing_materials` now references `#overgeared:tool_parts`,
  `heated_metals`, `hot_items` and `c:ingots/nuggets/plates/*` — addon mods
  (Spartan, Epic Knights) that register into those tags are covered
  automatically. All foreign entries are `required:false`. Fixes steel
  work paying untagged base XP.

### Added

- **`conditional_bonuses` on xp_source** — player-state-gated XP
  multipliers via the ability condition vocabulary (species/faction/
  condition/season-scoped material bonuses, no Java). Ships a ×1.5
  vampire-steel forging bonus (`vampirism:vampire` faction or the
  `lifepath:vampirism` condition) — compensation for vampires' silver/gold
  allergy.
- **A milestone every 10 levels.** Each of the 13 skills now also grants a
  milestone at 10/30/50/70/90/100 — a second thematic passive line per
  skill (e.g. *Smith's Strength* I–VI, *Quick Draw*, *Riverborn*), on top of
  the existing 20/40/60/80/95 landmark abilities. 78 new ability defs.
- **Soldier specialization** — defence `a` / archery `b` / athletics `c`,
  defence floor 30, signature *Hold the Line* (Resistance I below 3 hearts).
  Covers the previously orphan combat skills — no spec touched archery or
  defence before. **Hunter** now also starts with archery 10 and `b`
  aptitude (hunters shoot things).
- **Progression transparency (UX).** The skill detail screen now shows the
  real numbers: an "At your level" odds block (yield ×, quality tier, botch
  %, anvil cost, signature) and a scrollable **Roadmap** — every milestone
  level as a hoverable row revealing its band odds, unlocks, and XP cost
  ("4,675 XP total · 3,100 to go"). Skills list tooltips show a compact
  odds line. Powered by new sync fields (`roadmap`, `bands`,
  `bandThresholds`, `xpTotal`) resolved server-side — the client never
  trusts its own config.

### Fixed

- **Automatons can actually eat.** Allowed non-food items (`#automaton_foods`
  — iron ingots/nuggets) now consume on right-click and feed 3 hunger /
  0.4 saturation, gated by new `nutrition`/`saturation_modifier` diet fields
  (diets without them stay gate-only). Previously the items were legal but
  vanilla never started an eat on non-food items — dead clicks. Bread still
  correctly refuses with the diet deny message.
- **Character screen overflow** — content taller than the viewport used to
  push the Skills/Choose buttons off the bottom of the screen. The panel now
  caps below the viewport and the body scrolls (scissor + wheel + a thin
  scrollbar), title and key hints stay pinned.
- **"Defence" → "Defense"** in English UI text (skill name, Soldier
  strengths). Internal ids unchanged — datapack compatibility preserved.
- **Broken icon refs** — Defence/Engineering/Smithing skill icons pointed at
  `minecraft:textures/item/{shield,piston,anvil}.png`, which don't exist as
  flat item sprites (now: iron chestplate, redstone, anvil block texture).
  Eight ability defs referenced PNGs that never shipped (`anima_morph`,
  `hold_the_line`, and vanilla paths like `blue_ice`). A new test walks every
  shipped `icon` field and fails on dangling lifepath refs.
- **Morph picker icons** — spawn-egg texture paths like
  `minecraft:textures/item/cat_spawn_egg.png` don't exist in 1.21.1 (the egg
  is a tinted base sprite, not per-mob art). Morph icons now ship as real
  tinted vanilla spawn-egg sprites under `gui/morph/` — extracted from the
  client jar, tinted with the exact colors from `Items.<clinit>`.
- **Noise icons.** The ability/species icon sets were procedurally generated
  per-pixel noise — visually meaningless at 16×16 and the source of the
  "passives look weird" report. Regenerated as flat two-tone semantic
  glyphs (gear, bolt, flame, moon, paw…) tinted per species group, matching
  the skill-icon style. Generator lives at `tools/icons/make_icons.py`.
- **Milestone hover descriptions** — roadmap rows, milestone effects, and
  "Current bonuses" rows now carry the referenced ability's description on
  the wire (`Entry.description`), so hovering "Stone Skin I" or
  "Fine Calibration I" actually says what it does instead of just naming it.
- **`fabric:runClient` gets its own game dir + auto-join** — it used to share
  `fabric/run` with `runServer`, so a running dev server held file locks that
  made the client launch fail (`latest.log`, `.fabric/processedMods`). Now
  `run-client/` is separate and the client auto-connects to
  `localhost:25565` (same convention as `neoforge:runClient`).
- **Selection card body text was clipped.** Species descriptions and the
  green/red "+/-" specialization trade-off lines rendered at full font and
  ellipsized inside the 150px card — most of the text was hidden. Card body
  rows now render at 70% scale (four readable lines per card), and hovering a
  card without a description shows the full detail list as a tooltip, so no
  clipped line is permanently lost.

## [1.0.0-beta.10] — 2026-10-08

Outcome-scaling economy (M21–M25) + soft-dep compat layer.

Skill now shapes what you **produce**, not just what you earn. The player
doing the work gets the XP and the outcome — novices craft worse, masters
craft better, and experts sign their work.

### Added

- **Outcome scaling for 11 skills.** Every product-bearing skill maps rank
  bands (`untrained`…`legendary`) to output modifiers: count multiplier,
  failure chance, quality tier (`Crude`/`Poor`/`Fine`/`Masterwork` — visible
  in item lore), and `Crafted by <name>` signatures at expert+. Datapack-
  driven via `data/lifepath/outcome_rule/*.json` — see `docs/BALANCE.md`
  for the full matrix and `docs/DATAPACK_API.md` for the schema.
- **Smithing**: Overgeared forges bias native `ForgingQuality` (POOR→MASTER)
  by band; vanilla anvil repair/rename costs scale ±25%/−50%; failed rolls
  produce a `Crude` partial result at reduced count.
- **Cooking, engineering, scholarship**: crafted/smelted outputs scale
  (subjects discriminated so shared `crafting` activity doesn't collide).
- **Mining, woodcutting, foraging, farming**: drops roll probabilistically —
  at untrained ~40% of stack drops never land; at legendary ~60% bonus.
- **Hunting + archery**: kill loot scales; projectile kills use archery,
  melee uses hunting (`lifepath:animals` tag). Mob-grinder kills get
  vanilla drops — scaling requires a real player damage source.
- **Fishing**: catches scale per stack; at expert+ junk catches can upgrade
  to rolled fish (`junk_upgrade_chance` 25/40/60%).
- **`/lifepath debug skill <player> <skill> set <level>`** — admin/tester
  setter for band verification (uses the existing XP service path).
- **Pehkui species scale (soft-dep, #153)**: optional `scale` field on
  `species/` applies `pehkui:base` size when Pehkui is installed — shipped
  dwarf `0.7`, goliath `1.4`. Applied on species set, re-applied on
  join/respawn, persistent through death. Without Pehkui the field is
  inert — no classes load, no crash, identical behavior.
- **`lifepath:season` condition (soft-dep, #154)**: SpecNode condition
  composable anywhere conditions are — `seasons` list matches coarse
  (`winter`), sub-season (`early_winter`), and tropical (`wet`/`dry`)
  names via Serene Seasons when installed; fail-closed with a one-time
  warning when absent.

### Changed

- **Attribution audit** (`docs/ATTRIBUTION.md`): verified every XP producer
  credits the actor — Overgeared's anvil owner is the hammer-swinger, not
  the placer (bytecode-confirmed; earlier assumption corrected).

### For testers

- Try crafting/smelting/mining at a fresh character — expect fewer, `Crude`
  outputs and occasional botches. Then have a skilled player do it — more
  output, signed, better quality.
- Re-mine a block you placed: expect plain vanilla drops (anti-exploit).
- Verification matrix + live evidence: `docs/BALANCE.md`; tester steps:
  `docs/TESTING.md` → "Outcome scaling".

## [1.0.0-beta.9] — 2026-10-06

Tester bug-set remediation and a species balance pass. Every species now has
a kit worth picking — humans included.

### Added

- **Species kit list on the character screen.** The O screen permanently
  lists your species' strengths and weaknesses under its name. This is the
  root cause of the "Undead has nothing" report — diet and mob-neutrality
  rules were working but invisible.
- **Human: Resolve** — dropping below 3 hearts grants Regen II + Speed
  (120s cooldown).
- **Human: Fortune** — standing +1 luck.
- **Undead: Grave Vigour** — Strength I at night.
- **Undead: Rotting Touch** — attacks apply Wither (12s cooldown).
- **Iceborn: Flash Freeze** — active ability: freezes and slows all mobs
  within 4 blocks (90s cooldown). Iceborn's first active.

### Changed

- **Stoneform**: duration 5s → 30s, cooldown 240s → 120s.
- **Sink Like Stone**: gravity 0.08 → 0.16; swimming up can no longer beat
  the sink.
- **"Nether Vigor" renamed to Infernal Vigor** — the passive activates near
  fire, lava, and arid biomes; it never required the Nether.
- **Skill icons** replaced with vanilla item art (bow, pickaxe, piston,
  anvil…); **morph-form icons** use spawn-egg art. The previous custom
  sprites were unreadable at GUI scale.
- **Skill tooltips** now show the training hint alongside the description.

### For testers

- Check that the kit list on the O screen matches what actually happens
  for your species.
- New UI elements (kit lines, icons, tooltips) are verified logically but
  not pixel-checked — screenshot anything that looks wrong.
- Sink-vs-swim feel for dwarves is flagged for playtest.

## [1.0.0-beta.8] — 2026-10-05

Hidden species, diet enforcement, and a player/tester/admin journey pass —
exercised end-to-end on a live server with a headless client.

### Added

- **Phoenix — hidden species.** Offer a Totem of Undying to unlock it: fire
  immunity, flame heals you, flight while the inner fire burns. Death is a
  molt — you return frail and earthbound (weakness, slowness, no wings)
  until Rebirth Molt ends. Invisible in the picker until unlocked; admins
  manage it via `/lifepath unlock` and `character inspect`.
- **Frostbite (Iceborn)** replaces Frost Walk: attacks freeze the victim's
  frost meter and slow them (8s cooldown), driven by the new
  `damage_dealt` trigger and `victim` target resolver.
- **Ability-engine vocabulary**: `inventory_contains`, `food_level`,
  biome/temperature conditions; `freeze_ticks`, `feed`, `consume_item`,
  flight-grant actions. All content stays datapack-driven.
- **Skill panel** on the character screen lists every skill with level,
  progress, and aptitude.
- **`/lifepath help`** maps the admin command tree; unlocks now announce
  themselves ("Unlocked: Phoenix"); first-identity messages show your
  actual keybind names.
- Tester tooling: GitHub bug-report template and `docs/TESTING.md`
  checklist.

### Changed

- **Diets hard-block eating** — three enforcement layers (use-start,
  use-item, direct-eat backstop); denied food is never consumed. Denial
  messages are per-diet, and Automaton's `Devour Iron` turns nuggets and
  ingots into real meals.
- **Java namespace → `com.dwurdy`** (Maven group too). The `lifepath` mod
  id and datapack namespace are unchanged — worlds and datapacks keep
  working.

### Fixed

- MixinExtras owner-type crash on `damage_dealt` — found only by booting
  the packaged jar; dev runs never hit it.

## [1.0.0-beta.7] — 2026-10-01

Anima species can become an animal — picked once at species-select, locked
like the species itself.

### Added

- **Anima morph.** New `morph_form` content domain; a second picker step
  grants one form — fox, wolf, cat, rabbit, goat, panda, polar bear, or
  sheep — toggled by the `anima_morph` ability with a cooldown both ways.
- **You are the animal**: hitbox and eye height swap to the form's; HP
  carries proportionally both directions; stats mirror the mob's vanilla
  profile. A lethal hit force-demorphs and carries overflow damage onto
  your human bar instead of killing you.
- **Restrictions while morphed**: no mining, stations, crafting/furnace/
  anvil/trade/loom output (shift-click and double-click included), item
  use, or trading. Melee bite stays.
- Clients render a posed look-alike entity via vanilla entity data — other
  players see the morph with no extra packets.
- Admin surface: `/lifepath morph get|set|clear <player>`; `species set`
  keeps morph state consistent.

### Fixed

- Production hardening found only in the packaged jar: mixin refmaps
  declared for every injector type; malformed morph form ids fall back to
  vanilla dimensions instead of a zero-size hitbox; the hitbox seam ran
  dead code on `LivingEntity`; armor-stand interact-at denied; result-slot
  deny moved before the take (dupe vector); species swap retires a
  stranded morph.

## [1.0.0-beta.6] — 2026-09-30

Compatibility release — verified against the Rustic Craft 2 server files —
plus the skills payoff milestone.

### Added

- **Skill milestone abilities.** All 13 skills grant a tiered passive at
  levels 20/40/60/80/95 — mining digs faster, farming/fishing gain
  contextual luck, defence hardens, engineering reaches further, etc.
  Grants survive decay once earned.
- **Pack data sweep**: Naturalist and Hybrid Birds animals feed Hunting;
  Farmer's Delight meals/snacks/sweets feed Cooking; Create machines get
  engineering XP values and encumbrance weights; steel is properly heavy;
  vampire/werewolf abilities defer to Vampirism's faction system when that
  mod is installed.

### Fixed

- **Fake players no longer level skills** — a loader-neutral check gates
  every player-attributed XP path (Create deployers were earning real XP).
- **Overgeared forging awards Smithing XP** — forging completes inside the
  block entity with no vanilla event; a mod-gated mixin attributes the
  forge to its owner.
- **Farmer's Delight pot and skillet award Cooking XP** to whoever takes
  the food (no craft event on NeoForge).

### Changed

- **Encumbrance rebalance**: more leeway at low/mid load, same wall at the
  top; sacks 60%, backpacks 50%.

## [1.0.0-beta.5] — 2026-09-30

### Fixed

- Picker card tooltips rendered inside the grid scissor, so later cards
  painted over the tooltip and the frame clipped — visible as icons
  bleeding through on right-column hovers. The hovered tooltip now renders
  after the grid pass. **Take this build over beta.4.**

## [1.0.0-beta.4] — 2026-09-30

### Fixed

- Picker and character screens called `super.render` after drawing, so the
  framebuffer blur pass ran a second time — smearing every card and label
  while vanilla buttons stayed sharp. Widgets now render explicitly after
  content. **Take this build over beta.3.**

## [1.0.0-beta.3] — 2026-09-30

### Fixed

- The NeoForge jar bundled night-config via JarJar even though NeoForge
  provides it — launchers with Sinytra Connector died at discovery with
  `reads more than one module named com.electronwill.nightconfig.toml`.
  night-config is now compile-only on NeoForge. Fabric still bundles it
  (no TOML library on that loader). **NeoForge testers: take this build
  over beta.2.**

## [1.0.0-beta.2] — 2026-09-30

### Added

- **Guided selection screen** — species + specialization picker auto-opens
  on first join; cards show icon, name, description, and a "what you get"
  strip. Reopen via the Character screen's Choose button (key `O`;
  `client.toml onboarding_auto_open` toggles auto-open). `/lifepath` is
  admin-only — players never touch commands.
- **Picker UX**: draggable scrollbar, collapsing details strip, word-wrap
  with `…` + hover tooltips, green `+` / red `-` pros/cons on every card —
  plain language, no assumed Origins knowledge.
- **Specialization pros/cons** — same `+`/`-` lines: XP rates, decay
  floors, carry bonuses, trade-offs.
- **Full localization** — all player-facing text lives in editable YAML at
  `common/src/main/lang/*.yaml`; the build generates lang JSON. Content
  text travels as `translatableWithFallback`, so each client renders its
  own locale while custom datapack prose works with no lang file.
  Romanian (`ro_ro`) ships complete: 435/435 keys.
- Docs: `docs/TRANSLATIONS.md`, `docs/DATAPACK_API.md`
  (`strengths`/`weaknesses` on species + spec).

### Fixed

- Picker auto-open no longer races the terrain-loading screen.
- Screens call `super.render` — widgets always draw.
- Character-screen key moved `C` → `O` (vanilla save-toolbar conflict made
  `C` unreachable).

## [1.0.0-beta.1] — 2026-09-27

First public beta. Feature-complete through milestone M10.

### Added

- **Character core** — server-authoritative `PlayerCharacterData`:
  species, specialization, skills (XP, levels, aptitudes, protected
  floors), resources, conditions, attunements, unlocks, cooldowns.
  Per-player persistence with the v0→v1→v2 migration chain
  (`docs/MIGRATIONS.md`).
- **15 species** — open, `unlocked` (Phantom/Phoenix/Celestial gated by
  `unlock/` definitions), `admin_only`, and `hidden` visibility tiers.
- **13 specializations** — aptitude grades, XP/decay modifiers, signature
  abilities, protected floors, encumbrance capacity multipliers.
- **13 skills** — gathering/crafting/combat/physical/knowledge categories,
  milestone ladders, rank bands, per-grade aptitude multipliers.
- **Progression systems** — diminishing returns, real-time skill decay,
  encumbrance (inventory load → resource bands), diets, mob dispositions.
- **Ability engine** — 79 data-driven abilities over 22 conditions, 18
  actions, 3 targets; passive/active/event/damage-taken triggers.
- **Conditions & attunements** — staged conditions (Vampirism,
  Lycanthropy) and flat attunements (Air, Earth, Lightning), all
  data-defined.
- **UI** — character screen, skill list/detail, HUD, feedback events. The
  Abilities list binds the ability key to ACTIVE rows; pressing the key
  with nothing bound auto-picks the first owned active server-side.
- **Ops surface** — full `/lifepath` admin tree
  (`docs/ADMIN_COMMANDS.md`), 9 config files
  (`docs/CONFIGURATION.md`), datapack API for every content domain
  (`docs/DATAPACK_API.md`), live content validation on boot/reload.

### Known limitations (beta scope)

- Designed to run **without** Origins/Apoli/KubeJS — do not install them
  for baseline testing; the Overgeared smithing adapter is dormant without
  that mod.
- Multiplayer desync soak is the purpose of this beta — report character-
  state drift after death, relog, or dimension travel.
- `hidden` species and Celestial unlocks are intentionally admin-gated.
- Alchemy skill deferred (no brewing-station attribution primitive);
  Melee and Enchanting rejected at M8-3 (subsumed / too rare).
