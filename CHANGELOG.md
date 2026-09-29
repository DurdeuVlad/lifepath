# Changelog

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
  admin-only surface.
- **13 specializations** — aptitude grades, XP/decay modifiers, signature
  abilities, protected floors, encumbrance capacity multipliers.
- **13 skills** — gathering/crafting/combat/physical/knowledge categories,
  milestone ladders, rank bands, per-grade aptitude multipliers.
- **Progression systems** — diminishing returns (rolling signature windows),
  skill decay (5-band real-time ladder), encumbrance (inventory load →
  resource bands), diets, mob dispositions.
- **Ability engine** — 79 data-driven abilities over 22 conditions, 15
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
