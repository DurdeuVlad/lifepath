# Release Engineering (M11-1)

## Build

```bash
./gradlew clean build   # compile + test + assemble
```

Output: `build/libs/lifepath-<version>.jar` (+ `-sources.jar`). The jar
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

## Dependency metadata

`fabric.mod.json` `depends` — `fabricloader >=0.16.10`,
`minecraft ~1.21.1`, `java >=21`, `fabric-api >=0.116.0` — matches
`build.gradle` (`fabric-api 0.116.17`, loader 0.16.10). Embedded
`night-config` TOML is `include`d into the jar and correctly absent from
`depends`. No optional/`recommends` entries — all external-mod integration
(Overgeared, etc.) degrades at runtime, not at dependency resolution.
