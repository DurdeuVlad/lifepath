# Lifepath

A standalone character progression mod for **Minecraft 1.21.1 / Fabric**.

Species defines what a character *is*. Specialization defines what they *chose to focus on*. Skills record what they *actually practice*. Decay makes mastery something that must be maintained. Traits, conditions, attunements, and abilities compose on top — characters are developed through play, not picked from a list of finished builds.

> **Status:** pre-alpha — design and foundation phase. See the roadmap below.

## Documentation

| Document | Contents |
|---|---|
| [docs/GAMEDESIGN.md](docs/GAMEDESIGN.md) | Foundational design: character model, species, specializations, skills, aptitudes, decay, ability engine, UX |
| [docs/TIMELINE.md](docs/TIMELINE.md) | Milestone plan M0–M11, architecture contracts, execution rules, agent contract |
| [docs/RELEASE_ISSUES.md](docs/RELEASE_ISSUES.md) | Public-release & branding issue backlog (LP-REL-001…010) |

## Core contracts

- **Java = mechanics · Data = content · Config = balance** — the primary architectural separation.
- **Server-authoritative progression** — the server owns character state, XP, cooldowns, unlocks.
- **Composable primitives** — no bespoke Java class per species/skill/ability.
- **Zero-confusion UX** — normal gameplay must not require a wiki.
- **Integration-first compatibility** — external mods provide gameplay loops; Lifepath provides the progression layer. Overgeared is the reference Smithing case.

## Roadmap

`M0` Foundation → `M1` Character Core → `M2` Skill Framework → `M3` Progression & Decay → `M4` Ability Engine → `M5` Vertical Slice → `M6` UI → `M7` Hardening → `M8` Content Expansion → `M9` Advanced Systems → `M10` Stabilization → `M10.5` Beta/RC → `M11` CurseForge Release.

Full details, deliverables, and exit gates: [docs/TIMELINE.md](docs/TIMELINE.md).

## Tech

Minecraft 1.21.1 · Fabric Loader · Java 21 · Gradle

## License

Not yet selected — tracked under milestone M11.
