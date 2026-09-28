# Release Engineering (M11-1)

## Build

```bash
./gradlew clean build   # compile + test + assemble
```

Outputs (multi-loader, M13): `fabric/build/libs/lifepath-<version>-fabric.jar`
and `neoforge/build/libs/lifepath-<version>-neoforge.jar`, each with a
`-fabric-sources` / `-neoforge-sources` sources jar carrying the shared
`common/` sources. **NeoForge is the primary loader**; Fabric is secondary.
Every release attaches all four jars plus one `checksums.txt`. Each jar
root carries `LICENSE` (LGPL-3.0), `LICENSE.GPL` (the GPL-3.0 it extends —
LGPL-3.0 §4(b) requires both), and `NOTICE` (copyright statement).
Requires **JDK 21** (Temurin or equivalent) — Gradle wrapper is pinned
(`gradle/wrapper/gradle-wrapper.properties`), no other tooling needed. The
command is what CI runs and what produced `1.0.0-beta.1`.

## Versioning

- Scheme: **semver** `MAJOR.MINOR.PATCH` with `-beta.N` / `-rc.N` prerelease
  suffixes (`1.0.0-beta.1` is the first public beta).
- Single source of truth: `version=` in `gradle.properties` → interpolated
  into `fabric.mod.json` (`"version": "${version}"`) → jar filename. One
  edit bumps everything.
- Bump rules: patch = fixes only; minor = backward-compatible content/mechanics;
  major = save-format or API breakage (see `docs/MIGRATIONS.md` for the
  `data_version` side — mod version and data version are independent numbers).

## Changelog

`CHANGELOG.md` is **curated**, one `## <version> — <label>` section per
release (beta entry documents what's-in/limitations/reporting/distribution).
Written by hand at release time — not generated — because tester-facing notes
need judgement about scope. CI release notes are auto-generated as a starting
point; copy the curated section into the release body.

## CI

`.github/workflows/build.yml` — every push to `main` and every PR: Temurin 21,
`gradle/actions/setup-gradle` (validates `gradle-wrapper.jar` checksums and
manages the dependency cache), `./gradlew build` (compile + tests + jar),
uploads the jar and test reports as artifacts. Token is scoped
`contents: read`. A red build means compile or test failure — treat as
merge blocker.

`.github/workflows/release.yml` — pushing a `v*` tag fails fast unless the
tag matches `version=` in `gradle.properties` (`v1.0.0-beta.1` ↔
`1.0.0-beta.1`), then builds clean, writes `sha256` checksums, and creates
a **draft** GitHub Release with the jars + checksums + generated notes
(`fail_on_unmatched_files` is on — a missing artifact fails the workflow
instead of shipping an empty release). Draft (not published) so the
curated changelog section is attached before anyone sees it. Tagging:

```bash
git tag v1.0.0-beta.1 <commit> && git push origin v1.0.0-beta.1
```

### CurseForge publishing

`release.yml` also publishes both loader jars to CurseForge via
`Kir-Antipov/mc-publish@v3.3` — **one upload step per jar** so each file
gets its own loader tag (NeoForge / Fabric), `1.21.1` + `Java 21` game
versions, `CHANGELOG.md` as the file changelog, and a release type derived
from the version string (`*alpha*` → alpha, `*beta*` → beta, else
release). The Fabric file declares `fabric-api` as a required dependency.
Sources jars are never uploaded.

The steps are gated on `vars.CURSEFORGE_PROJECT_ID` — with the variable
unset they skip cleanly and the GitHub draft release still runs. One-time
setup:

1. **Create the CurseForge project once by hand** — the create form
   requires a logo upload (`art/branding/lifepath_icon_512.png`) which
   needs a native file dialog; automation can't reach it. Fill summary,
   categories, links, banner (`lifepath_banner.png`) and gallery
   (`art/release-gallery/`) in the same session — copy lives in
   `docs/CURSEFORGE.md`.
2. Grab the project's numeric **Project ID** from its overview page →
   repo Settings → Variables → `CURSEFORGE_PROJECT_ID`.
3. CurseForge authors dashboard → account → API tokens → create token →
   repo Settings → Secrets → `CURSEFORGE_API_TOKEN`.
4. Next `v*` tag (or `workflow_dispatch` re-run of the release workflow)
   uploads both jars automatically. The dispatch trigger exists precisely
   for the retroactive first publish after secrets land.

Manual upload of future versions is no longer needed — only the initial
project creation stays human.

## Dependency metadata

`fabric.mod.json` `depends` — `fabricloader >=0.16.10`,
`minecraft ~1.21.1`, `java >=21`, `fabric-api >=0.116.0` — matches
`build.gradle` (`fabric-api 0.116.17`, loader 0.16.10). Embedded
`night-config` TOML is `include`d into the jar and correctly absent from
`depends`. No optional/`recommends` entries — all external-mod integration
(Overgeared, etc.) degrades at runtime, not at dependency resolution.
