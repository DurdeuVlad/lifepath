# Changelog

## 1.0.0-beta.5 — picker tooltip fix

Hotfix over beta.4: card description tooltips were rendered inside the
card loop while the grid scissor was still active, so later cards painted
their icon/text over the tooltip and the tooltip frame got clipped —
visible as icons bleeding through and text overlapping on right-column
hovers. The hovered card's tooltip now renders after the grid pass.
Take this build over beta.4.

## 1.0.0-beta.4 — GUI blur fix

Hotfix over beta.3: the picker and character screens drew their content
and then called `super.render`, whose internal `renderBackground` runs the
framebuffer blur pass a second time — smearing every card, title, and
detail line while vanilla buttons stayed sharp. Widgets now render
explicitly after our content, so the blur only ever touches the world.
Everyone on beta.3 sees it — take this build.

## 1.0.0-beta.3 — NeoForge launch fix

Hotfix over beta.2: the NeoForge jar bundled night-config via JarJar even
though NeoForge already provides it, so launchers that also run Sinytra
Connector died during mod discovery with `reads more than one module named
com.electronwill.nightconfig.toml`. night-config is now compile-only on
NeoForge (loader-provided at runtime). Fabric still bundles it — Fabric
ships no TOML library. NeoForge testers on beta.2 should take this build.

## 1.0.0-beta.2 — guided onboarding + full localization

Second beta build: the species/specialization pick is now a real GUI flow,
and every player-facing string is translatable.

### What's in (delta over beta.1)

- **Guided selection screen** — the species + specialization pick auto-opens
  on first join; cards show icon, name, description, and a "what you get"
  strip. Reopen any time via the Character screen's Choose button (key `O`;
  `client.toml onboarding_auto_open` toggles auto-open). `/lifepath` stays
  admin-only — players never touch commands.
- **Picker UX** — draggable scrollbar, details strip that collapses when
  nothing is selected, word-wrapped descriptions with `…` + hover tooltips,
  green `+` / red `-` pros/cons lines on every card (diet, environment,
  fragility — plain language, no assumed Origins knowledge).
- **Specialization pros/cons** — spec cards show the same `+`/`-` lines:
  XP rates, decay floors, carry bonuses, honest trade-offs.
- **Full localization** — all player-facing text lives in editable YAML at
  `common/src/main/lang/*.yaml`; the build generates Minecraft lang JSON.
  Content text (species, abilities, skills, resource bands, conditions,
  attunements) travels as `translatableWithFallback` components, so every
  client renders its own locale while custom datapack prose still works
  with no lang file. Romanian (`ro_ro`) ships complete: 435/435 keys.
- **Docs** — `docs/TRANSLATIONS.md` (add a language, content key convention),
  `docs/DATAPACK_API.md` (`strengths`/`weaknesses` on species + spec).

### Fixed

- Picker auto-open no longer races the terrain-loading screen.
- Screen render calls `super.render` — buttons and widgets always draw.
- Character-screen default key moved `C` → `O` (vanilla save-toolbar
  conflict made `C` unreachable).

## 1.0.0-beta.1 — public beta / release candidate

First build intended for real-player testing. Feature-complete through
milestone M10 — every roadmap system below is shipped and exercised.

### What's in

- **Character core** — server-authoritative `PlayerCharacterData`: species,
  specialization, skills (XP, levels, aptitudes, protected floors), resources,
  conditions, attunements, unlocks, cooldowns. Persisted per player with the
  v0→v1→v2 migration chain (`docs/MIGRATIONS.md`).
- **15 species** — open, `unlocked` (Phantom/Phoenix/Celestial gated by
  `unlock/` definitions), `admin_only`, and `hidden` visibility.
- **Guided selection screen** — species and specialization are picked on a
  card picker that auto-opens on first join (reopen via the Character
  screen's Choose button; `client.toml onboarding_auto_open` toggles the
  auto-open). Shows name, description, what you get, and locked reasons;
  choices apply through server-validated requests — `/lifepath` is now an
  admin-only surface. The card list has a draggable scrollbar; the
  "What you get" strip only exists while a card is selected; each option's
  `strengths`/`weaknesses` datapack fields render as green `+` / red `-`
  lines so benefits and costs are legible at a glance.
- **YAML translations** — all player-facing text lives in
  `common/src/main/lang/*.yaml`; the build generates Minecraft `lang/*.json`.
  Romanian (`ro_ro`) ships complete: UI chrome plus every bundled species,
  specialization, ability, skill, resource band, condition and attunement.
  Content text travels as `translatableWithFallback` — each client renders
  its own locale for bundled content while custom datapack prose renders
  as-is. See `docs/TRANSLATIONS.md`.
- **Specialization pros/cons** — specialization cards show green `+` / red
  `-` lines like species cards (XP rates, decay floors, carry bonuses,
  honest trade-offs), both on the card and in the details strip.
- **13 specializations** — aptitude grades, XP/decay modifiers, signature
  abilities, protected floors, encumbrance capacity multipliers.
- **13 skills** — gathering/crafting/combat/physical/knowledge categories,
  milestone ladders, rank bands, per-grade aptitude multipliers.
- **Progression systems** — diminishing returns (rolling signature windows),
  skill decay (5-band real-time ladder), encumbrance (inventory load →
  resource bands), diets, mob dispositions.
- **Ability engine** — 79 data-driven abilities over 22 conditions, 18
  actions, 3 targets; passive/active/event/damage-taken triggers.
- **Conditions & attunements** — staged conditions (Vampirism, Lycanthropy)
  and flat attunements (Air, Earth, Lightning), all data-defined.
- **UI** — character screen, skill list/detail, HUD element, feedback events.
  The character screen's Abilities list is clickable (ACTIVE rows bind the
  ability key, passives are tagged/dimmed); pressing the key with nothing
  bound auto-picks the first owned active ability server-side.
- **Ops surface** — full `/lifepath` admin tree (`docs/ADMIN_COMMANDS.md`),
  9 config files (`docs/CONFIGURATION.md`), datapack API for every content
  domain (`docs/DATAPACK_API.md`), live content validation on boot/reload.

### Known limitations (beta scope)

- Designed to run **without** Origins/Apoli/KubeJS — none are required and
  none should be installed for baseline testing; the Overgeared smithing
  adapter is dormant without that mod.
- Live multiplayer desync soak is *the purpose of this beta* — report any
  character-state drift (species/skills/resources appearing wrong after
  death, relog, or dimension travel).
- `hidden` species and Celestial unlocks are intentionally admin-gated.
- Alchemy skill is deferred (no brewing-station attribution primitive yet);
  Melee and Enchanting were rejected at the M8-3 gate (subsumed / too rare).

### Where to report

GitHub Issues — <https://github.com/DurdeuVlad/lifepath/issues> — label
`bug` with repro steps, `/lifepath debug character <player>` output, and the
server log section around the failure.

### Distribution path

**GitHub Releases** for this beta — a draft release tagged `v1.0.0-beta.1`
carrying `lifepath-1.0.0-beta.1.jar` and a `checksums.txt` produced by the
release workflow (those checksums are authoritative). Reference hash of
the local verification build on this commit: sha256
`b63fbb37de1fbdd474a9092da7c58cd7643eafa7332a0d7a105cd1a884167678` — the
released jar may hash differently if the tag lands on a later commit.
CurseForge public listing is the M11 release milestone, not the beta path.

### Pre-flight RC evaluation (M10-4 evidence)

| RC criterion | Status | Evidence |
|---|---|---|
| No known save corruption | ✅ | Backup-on-decode-failure + defaults fallback, suite + `RELEASE_MATRIX.md` #2/#9 |
| No core progression desync | ✅ | Server-authoritative state + S2C snapshots; M7-2 race/rebind fixes tested — live multi-client soak is what this beta is for |
| No critical onboarding confusion | ✅ | M6-4 zero-confusion UX + `species choose` guidance strings; `hidden`/`admin_only` species never surface to players |
| No major multiplayer exploit | ✅ | `RELEASE_MATRIX.md` criterion 1 — diminishing returns, re-mine tracking, no known open exploit |
| Build representative | ✅ | `clean build` from this commit → `lifepath-1.0.0-beta.1.jar` with `fabric.mod.json` version `1.0.0-beta.1`; dedicated-server boot + reload verified in-matrix |
