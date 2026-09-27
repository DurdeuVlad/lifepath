# Lifepath

**Build a character by playing, not by picking from a list.** Lifepath is a
character-progression mod for Minecraft 1.21.1 (NeoForge + Fabric) where who
you are, what you focus on, and what you actually practice all matter — and
mastery is something you maintain, not a checkbox you tick once.

> **Status:** approaching 1.0 — feature-complete, in release-candidate
> stabilization. Things may still shift before the first stable release.

## The three pillars

- **Species is what you are.** Fifteen species — from amphibian to undead —
  each with its own innate traits and abilities. Species defines your
  starting canvas.
- **Specialization is your starting focus — it does not lock you out of
  anything.** Thirteen trades (Blacksmith, Hunter, Scholar, …) give you
  aptitude and a protected skill floor. A Blacksmith reaches Smithing
  mastery faster and can't fall below it — but anyone can learn anything.
- **Skills are what you actually practice.** Thirteen skills level by doing:
  mine ores to improve Mining, cook to improve Cooking. Skill milestones
  unlock new abilities as you climb.

## How progression works

- Your **aptitude** in a skill (D through S) shapes how fast you learn it
  and how slowly it fades — your species and specialization set the
  starting aptitudes, nothing else.
- Skills decay **only if you stop using them**, only after a grace period,
  and mostly at high mastery — you never wake up having forgotten the
  basics. Your specialization's **protected floor** means your trade skills
  can't decay below their guaranteed level.
- Everything is driven by ordinary play: XP comes from doing the activity
  the skill is about.

## What you'll see

- A **character screen** with your species, specialization, traits,
  conditions, and attunements — each with its own icon.
- A **skills screen** showing every skill, its level band, and its milestone
  progress; click through for detail.
- A **HUD** with resource bars and cooldown/state icons, kept out of the way.
- All server-authoritative: progress is owned by the world, safe on
  dedicated servers, and persists across deaths.

## Install

Lifepath ships one jar per loader — **NeoForge is the primary loader**,
Fabric is supported secondarily. Same version, same content, same features.

| Loader | File | Requirements |
|---|---|---|
| **NeoForge** (primary) | `lifepath-<ver>-neoforge.jar` | NeoForge 21.1+ for Minecraft 1.21.1 |
| **Fabric** (secondary) | `lifepath-<ver>-fabric.jar` | Fabric Loader 0.16.10+ **and** Fabric API 0.116.0+ |

1. Install the loader for **Minecraft 1.21.1** (NeoForge 21.1+ or Fabric
   0.16.10+; Fabric also needs Fabric API).
2. Drop the matching Lifepath jar into `mods` — on **both** the client and
   the server (the server owns your character's progress).
3. Java 21 required.

## First minutes

1. Start a world and open the character screen (default key `C` — rebind
   under Controls → "Lifepath").
2. Pick a species with `/lifepath species choose` — tab-completion lists
   the species, and its description prints when you pick
   (hover the species row on the character screen to re-read it).
3. Specializations are assigned by a server admin
   (`/lifepath specialization set`) — playing solo with cheats, set your
   own. It's a head start, not a contract: you can still train anything.
4. Go play. Skills level from play — watch them grow on the skills screen,
   and press the ability key (default `G`) to fire an active ability;
   click an ability row on the character screen to choose which.

## Compatibility

Lifepath is **standalone** — no Origins, no other progression mod required.
It's designed to sit underneath content mods that provide gameplay loops:
an **Overgeared integration adapter** ships in the jar and activates only
when the partner mod is present. Verified live on NeoForge 1.21.1 — crafting
an Overgeared alloy furnace grants Smithing XP (`docs/evidence/m13-6/`). On
Fabric the adapter stays dormant: Overgeared ships no Fabric 1.21.1 build —
see `docs/COMPAT.md` for the honest state.

## Support

- Bugs and feedback: [GitHub Issues](https://github.com/DurdeuVlad/lifepath/issues)
- Datapack authors and server admins: the `docs/` folder covers the full
  content API, configuration, and admin commands.

## For datapack authors & admins

| Document | Contents |
|---|---|
| [docs/GAMEDESIGN.md](docs/GAMEDESIGN.md) | Design intent: character model, species, skills, decay, ability engine |
| [docs/API.md](docs/API.md) | The frozen 1.0 public surface: domains, ids, commands, config keys, wire ids |
| [docs/DATAPACK_API.md](docs/DATAPACK_API.md) | Content schema for every domain + worked example |
| [docs/ABILITIES.md](docs/ABILITIES.md) | Ability primitive vocabulary (conditions/actions/targets) |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every config key, default, range, and reload behavior |
| [docs/ADMIN_COMMANDS.md](docs/ADMIN_COMMANDS.md) | The `/lifepath` command tree |
| [docs/MIGRATIONS.md](docs/MIGRATIONS.md) | Save-compatibility rules |
| [docs/COMPAT.md](docs/COMPAT.md) | External-mod compatibility validation |
| [docs/TIMELINE.md](docs/TIMELINE.md) | Milestone plan M0–M11 and execution contracts |
| [docs/VISUAL_IDENTITY.md](docs/VISUAL_IDENTITY.md) | The art contract for generated assets |

## Tech

Minecraft 1.21.1 · NeoForge (primary) + Fabric (secondary) · Java 21 · Gradle

## License

Copyright (C) 2026 Vlad Durdeu — see `NOTICE`.

**LGPL-3.0-only** — full text in `LICENSE`, plus the GPL-3.0 it extends in
`LICENSE.GPL`.
