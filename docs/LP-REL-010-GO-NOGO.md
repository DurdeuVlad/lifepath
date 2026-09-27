# LP-REL-010 — Public release go/no-go review (1.0.0-beta.1)

Date basis: this commit. Judged against the LP-REL-001…009 checklist.

## Verdict: **GO for GitHub-draft beta — NO-GO for the CurseForge public listing**

The artifacts, packaging, and in-game evidence are complete and verified for a
**beta distribution via GitHub Releases** (the path CHANGELOG.md already
declares). Two upstream/environmental blockers stand between this build and
an honest CurseForge public page; neither touches build quality.

## Checklist

| Item | Status | Evidence |
|---|---|---|
| README complete | ✅ | `README.md` — hook, features in plain language, species/spec/skills model, progression+decay, installs, supported versions, compat philosophy, first-run quickstart, support links. Every claim verified against the repo. |
| CurseForge description complete | ✅ | `docs/CURSEFORGE.md` — paste-ready summary + full description; no unsupported Overgeared claim. |
| Project icon | ✅ | `art/branding/lifepath_icon_512.png` + `lifepath_icon_32.png` (exact NN downscale, verified pixel-identical); packaged at `assets/lifepath/icon.png`. |
| CurseForge banner | ✅ | `art/branding/lifepath_banner.png` 1200×400 — wordmark + 3 shipped glyphs composited at 16× NN (pixel-verified against source textures). |
| Gallery screenshots | ✅ | `art/release-gallery/` — 8 real in-game captures + captions + provenance. Species/spec selection shown via the real command surface; no fake pickers. |
| Anti-slop review | ✅ | `docs/LP-REL-007-ANTI-SLOP.md` — itemized pass per public asset; human icon + 7 fuchsia-residue icons regenerated/cleaned this cycle. |
| Overgeared example validated | ❌ **BLOCKED** | No Overgeared build exists for Fabric 1.21.1 (Fabric = 1.20.1, 1.21.1 = NeoForge only). Adapter absent-behavior proven graceful by `OvergearedCompatTest` (3 green). Public copy does NOT claim the integration. Cannot validate a runtime that cannot exist. |
| Release JAR verified | ✅ | `lifepath-1.0.0-beta.1.jar` — 608 entries, zero `lifepath_test` leakage, zero audit/chrome assets, 136 icon textures ship. Test fixtures relocated to `src/test/resources`. |
| Metadata verified | ✅ | `gradle.properties`/`fabric.mod.json`/`CHANGELOG` all `1.0.0-beta.1`; deps: Minecraft ~1.21.1, Loader ≥0.16.10, Fabric API ≥0.116.17, Java ≥21; **no** Origins-family requirement; LGPL-3.0-only + LICENSE.GPL + NOTICE in jar. |
| Changelog ready | ✅ | `CHANGELOG.md` — beta.1 scope, known limitations, distribution path, RC table. |
| Support/issues link | ✅ | README + `docs/CURSEFORGE.md` → github.com/DurdeuVlad/lifepath/issues. |
| Public copy proofread | ✅ | README + CurseForge copy written plain-language-first; Overgeared wording verified against `docs/COMPAT.md`. |
| Ability key path | ✅ (fixed this cycle) | `G` was a dead key — nothing called `ClientAbilityState.select()`. Now: click an ACTIVE ability row on the character screen, or press the key for the server-side first-active pick. Verified live: bound `> Frost Walk`, fired, 120 s cooldown row, water → frosted ice. |

## M11 engineering gate

| Gate | Status | Evidence |
|---|---|---|
| Clean build | ✅ | `./gradlew clean build` — 346 tests, 0 failures. |
| Fresh client boot | ✅ | Dev client booted, world joined, screens + HUD + ability verified live (gallery). |
| Dedicated server | ⚠️ exercised earlier | Server boot + `/reload` verified in `RELEASE_MATRIX.md` (M10-4); not re-run this cycle — flag for release day. |
| World upgrade / migration | ✅ | v0→v1→v2 chain + `CharacterPersistence` tests; documented in `docs/MIGRATIONS.md`. |
| Optional integrations graceful | ✅ | `OvergearedCompatTest` — absent mod = no crash, no broken state. |
| No hardcoded content exceptions | ✅ | Content loads through data registries; validation at boot/reload. |
| Java/Data/Config separation | ✅ | Enforced by architecture + M-series tests. |
| No-wiki normal play | ✅ | Screens are self-explanatory; command suggestions drive selection; key hint is on-screen. |

## Remaining blockers for CurseForge-public

1. **#61 Overgeared validation** — upstream gap, not ours. Options: (a) ship
   listing with an honest "adapter dormant until a Fabric build exists" note —
   already what CURSEFORGE.md does; (b) hold listing until Overgeared ships
   Fabric 1.21.1. Recommend (a) — the copy is already accurate.
2. **#50/#51 real-player validation + RC gate** — requires actual players.
   This IS the beta's purpose; the GitHub-draft path exists precisely so that
   gate can run. Cannot be declared done without real external sessions.

## Recommendation

Publish `v1.0.0-beta.1` as a **GitHub draft release** (tag the release commit)
and collect real-player feedback. Hold the CurseForge public listing until
(a) #50/#51 evidence exists and (b) the Overgeared sentence can be validated
or is dropped to a dormant-adapter note — already the current wording.
