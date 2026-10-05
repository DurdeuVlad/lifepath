# Testing Lifepath — Beta verification guide

How to verify a beta build and file reports we can act on. The evidence
convention: **a claim needs a repro or a screenshot** — see
`docs/evidence/m13-6/` for the shape (one image per claim).

## Reporting

- File bugs at [GitHub Issues](https://github.com/DurdeuVlad/lifepath/issues)
  — the bug template asks for loader, version, repro, and evidence.
- Get your mod version in-game: `/lifepath version`.
- Report on the loader you actually ran; if it reproduces on both
  NeoForge and Fabric, say so — that's signal.
- Broken datapack content? `/lifepath reload` prints a per-domain
  OK/FAIL summary — paste it into the report.

## Beta 8 verification checklist

Each fix below is shippable if it reproduces as described on a real
server (dev-world-only proof doesn't count — that bit us in beta.7).

| Area | How to verify |
|---|---|
| Iceborn Frostbite | Hit a mob in melee → victim gains freeze ticks + Slowness. No frost path on water — Frost Walk is gone. |
| Phoenix flight | Unlock via Totem of Undying use → species appears in picker → `Unlocked: Phoenix` chat line. Flight drains flame; below 20 you drop. |
| Phoenix rebirth | Die as Phoenix → `Gained: Rebirth` → 120s frail (small, weak, earthbound) → True Form restored. |
| Diet hard-block | As Automaton, try to eat bread → actionbar "only iron nourishes you", item never consumed. Eat an iron ingot via Devour Iron → hunger restored. |
| Skill detail UI | Character screen (O) → Skills → a skill → panel shows milestone effects; resize the window small → panel clips, nothing overflows. |
| Icons | Cards, HUD rows, skill list show drawn silhouettes — no text placeholders. |
| Key hint | Pick a species → chat prints the Character/Ability keybind names once. |

## Admin-side checks

- `/lifepath` bare → version + a pointer to `/lifepath help`.
- `/lifepath reload` after a datapack edit → per-domain OK/FAIL lines.
- `/lifepath character reset <player> confirm` — refuses without `confirm`.
