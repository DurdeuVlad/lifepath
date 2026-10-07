# Changelog

All notable changes to Lifepath, newest first. Each release ships two jars on
GitHub Releases: `-neoforge` for NeoForge 21.1.x and `-fabric` for Fabric
0.16.x, both on Minecraft 1.21.1. SHA-256 checksums are attached to each
release.

Report bugs: <https://github.com/DurdeuVlad/lifepath/issues> — use the "Bug
report" template, include loader, version, and repro steps.

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
