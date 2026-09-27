# Lifepath

A standalone character progression mod for **Minecraft 1.21.1 / Fabric**.

Species defines what a character *is*. Specialization defines what they *chose to focus on*. Skills record what they *actually practice*. Decay makes mastery something that must be maintained. Traits, conditions, attunements, and abilities compose on top — characters are developed through play, not picked from a list of finished builds.

> **Status:** approaching 1.0 — feature-complete through M9, in M10 stabilization (API frozen, migrations documented). Not yet released.

## Documentation

| Document | Contents |
|---|---|
| [docs/GAMEDESIGN.md](docs/GAMEDESIGN.md) | Foundational design: character model, species, specializations, skills, aptitudes, decay, ability engine, UX — *design intent; shipped reality below* |
| [docs/TIMELINE.md](docs/TIMELINE.md) | Milestone plan M0–M11, architecture contracts, execution rules, agent contract |
| [docs/API.md](docs/API.md) | 1.0 API freeze: the enumerated public surface (data domains, ids, commands, config keys, wire ids, extension points, saved-data schema) |
| [docs/DATAPACK_API.md](docs/DATAPACK_API.md) | Datapack-author API: every content domain's schema, matching model, and a worked example |
| [docs/ABILITIES.md](docs/ABILITIES.md) | Ability-vocabulary reference: every condition/action/target primitive and its params |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every `config/lifepath/*.toml` file, key, default, range, effect, and reload behavior |
| [docs/ADMIN_COMMANDS.md](docs/ADMIN_COMMANDS.md) | The full `/lifepath` command tree, permissions, and examples |
| [docs/MIGRATIONS.md](docs/MIGRATIONS.md) | Save-compatibility rules: the dataVersion chain, removed-id fallbacks, how to add the next migration |
| [docs/RELEASE_MATRIX.md](docs/RELEASE_MATRIX.md) | M10-4 evidence: the nine stabilization tests and seven 1.0 release criteria, per-cell verdicts |
| [docs/RELEASE_ENGINEERING.md](docs/RELEASE_ENGINEERING.md) | Build/versioning/changelog/CI — reproducible release process |
| [docs/MULTIPLAYER.md](docs/MULTIPLAYER.md) | Server-authoritative correctness: sync, persistence, respawn ownership |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Profiling harness and explicit tick budgets |
| [docs/COMPAT.md](docs/COMPAT.md) | External-mod compatibility validation (Origins/Apoli absent, graceful degradation) |
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
