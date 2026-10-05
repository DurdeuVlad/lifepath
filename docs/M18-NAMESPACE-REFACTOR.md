# Lifepath — M18: Namespace Refactor (`io.github.durdeuvlad` → `com.dwurdy`)

> Motivation: the project now lives under the `dwurdy.com` domain, so the
> Java package and Maven group move off the GitHub-derived
> `io.github.durdeuvlad` coordinates to domain-derived `com.dwurdy`.
> Status: **closed — landed and verified.**

---

## Goal / System / Constraints / Evaluation

- **Goal:** every Java package is `com.dwurdy.lifepath.*`, the Gradle group
  is `com.dwurdy`, and no `io.github.durdeuvlad` reference survives outside
  git history and generated `build/`/`logs/` output.
- **System:** mechanical rename — directory move per source root, then a
  single textual replacement across sources, resources, and docs. No code
  behavior changes.
- **Constraints:** **mod ID stays `lifepath`.** `data/lifepath/`,
  `assets/lifepath/`, `fabric.mod.json` `"id"`, `neoforge.mods.toml` modid,
  `archives_base_name`, and every `lifepath:` namespaced registry ID are
  untouched — changing the mod ID would invalidate datapacks and saved
  characters. Author/contact strings (`DurdeuVlad`, GitHub repo URLs) are
  not package names and stay.
- **Evaluation:** `./gradlew build` green on all modules; `grep -r
  "durdeuvlad"` over tracked sources returns zero matches.

## Scope

| In scope | Out of scope |
|---|---|
| 255 `.java` files (package + imports) | mod ID / datapack namespace `lifepath` |
| 6 source-root directory trees (`io/github/durdeuvlad` → `com/dwurdy`) | GitHub org/repo URLs (`github.com/DurdeuVlad`) |
| `gradle.properties` `group` | `archives_base_name=lifepath` |
| `fabric.mod.json` entrypoints (3) | CurseForge/Modrinth listing slugs |
| 5 mixin JSONs (`package` + client `plugin` fields) | historical changelog prose |
| `META-INF/services` SPI filename + FQCN contents | |
| `org.junit.jupiter.api.extension.Extension` content | |
| `docs/COMPAT.md` API example | |

## Issues

- **M18-1** — Rename package tree `io.github.durdeuvlad.lifepath` →
  `com.dwurdy.lifepath` in all six source roots; update mixin `package`/
  `plugin` fields, Fabric entrypoints, SPI descriptor, JUnit extension
  registration, Gradle `group`, and `docs/COMPAT.md`.

## Acceptance criteria

- `./gradlew build` — clean compile + full test suite on common, fabric,
  neoforge.
- `grep -rln "io.github.durdeuvlad" . | grep -v build | grep -v .git` —
  zero results.
- Loader metadata resolves: entrypoint class names in `fabric.mod.json`
  match moved classes; mixin `package`/`plugin` fields match moved classes;
  SPI filename matches the interface FQCN.

## Resolution

Landed in a single pass: directory moves + textual rename, then
`./gradlew build` green (all modules, all tests). No behavior change;
mod ID, datapack namespace, and saved-data formats untouched.
