# LP-REL-010 — Public release go/no-go review (1.0.0-beta.1)

Date basis: this commit. Judged against the LP-REL-001…009 checklist.

## Verdict: **GO for GitHub-draft beta — NO-GO for the CurseForge public listing**

The artifacts, packaging, and in-game evidence are complete and verified for a
**beta distribution via GitHub Releases** (the path CHANGELOG.md already
declares). Two upstream/environmental blockers stand between this build and
an honest CurseForge public page; neither touches build quality. (Post-review:
the Overgeared blocker resolved on NeoForge — #138; one blocker remains.)

## Checklist

| Item | Status | Evidence |
|---|---|---|
| README complete | ✅ | `README.md` — hook, features in plain language, species/spec/skills model, progression+decay, installs, supported versions, compat philosophy, first-run quickstart, support links. Every claim verified against the repo. |
| CurseForge description complete | ✅ | `docs/CURSEFORGE.md` — paste-ready summary + full description; no unsupported Overgeared claim. |
| Project icon | ✅ (regenerated post-review) | `art/branding/lifepath_icon_512.png` + `lifepath_icon_32.png` (NN downscale); packaged at `assets/lifepath/icon.png` (128). Codex AI source → uniform pixel-grid post-process, 10 colors, 64px-legible — provenance in `art/branding/candidates/PROVENANCE.txt`. |
| CurseForge banner | ✅ (regenerated post-review) | `art/branding/lifepath_banner.png` 1200×400 — LIFEPATH wordmark + stairway/waypoint motif, same Codex AI + pixel-grid pipeline as the icon (11 colors); provenance in `art/branding/candidates/PROVENANCE.txt`. |
| Gallery screenshots | ✅ | `art/release-gallery/` — 8 real in-game captures + captions + provenance. Species/spec selection shown via the real command surface; no fake pickers. |
| Anti-slop review | ✅ | `docs/LP-REL-007-ANTI-SLOP.md` — itemized pass per public asset; human icon + 7 fuchsia-residue icons regenerated/cleaned this cycle. |
| Overgeared example validated | ✅ **Resolved post-beta on NeoForge** (was ❌ BLOCKED at review time) | `overgeared-neoforge-1.21.1-1.6.19` + `lifepath-1.0.0-beta.1` co-loaded on NeoForge 21.1.252; adapter active via `ModList`; crafting `overgeared:alloy_furnace` → `lifepath:smithing` xp=0.5 with diminishing key `lifepath:smithing\|overgeared:alloy_furnace`; absent-path clean. Evidence: `docs/evidence/m13-6/`, issues #138/#61. Fabric remains structurally N/A — no Fabric 1.21.1 Overgeared build exists; copy scopes the claim to NeoForge. |
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

1. ~~**#61 Overgeared validation**~~ — **resolved post-beta** (issue #138):
   live co-load + craft → Smithing XP proven on NeoForge 21.1.252
   (`docs/evidence/m13-6/`). The Fabric-side gap is upstream and permanent
   for 1.21.1 (no Fabric Overgeared build); public copy now says exactly that —
   "verified live with Overgeared on NeoForge; dormant on Fabric." Nothing
   further to validate.
2. **#50/#51 real-player validation + RC gate** — requires actual players.
   This IS the beta's purpose; the GitHub-draft path exists precisely so that
   gate can run. Cannot be declared done without real external sessions.

## Recommendation

Publish `v1.0.0-beta.1` as a **GitHub draft release** (tag the release commit)
and collect real-player feedback. Hold the CurseForge public listing until
#50/#51 real-player evidence exists. (Post-review update: the Overgeared
condition is now met — validated on NeoForge 1.21.1 in issue #138, evidence
in `docs/evidence/m13-6/`; only the real-player gate remains.)
