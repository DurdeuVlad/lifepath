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
| Iceborn Flash Freeze | As Iceborn press the ability key → every mob within ~4 blocks freezes solid + slows (90s cooldown). |
| Phoenix flight | Unlock via Totem of Undying use → species appears in picker → `Unlocked: Phoenix` chat line. Flight drains flame; below 20 you drop. |
| Phoenix rebirth | Die as Phoenix → `Gained: Rebirth` → 120s frail (small, weak, earthbound) → True Form restored. |
| Diet hard-block | As Automaton, try to eat bread → actionbar "only iron nourishes you", item never consumed. Eat an iron ingot via Devour Iron → hunger restored. |
| Undead kit | As Undead: zombies/skeletons ignore you; rotten flesh eats, bread doesn't; **at night you hit harder** (Strength) and your strikes apply a brief Wither (12s cooldown); sun still burns you in daylight. |
| Human kit | As Human: drop below ~3 hearts (any cause) → a surge of Regen + Speed fires (once per 2min); you carry standing luck. |
| Dwarf Stoneform | Press it → Resistance II + Slowness for **30s**, cooldown **120s** (was 5s/240s). |
| Dwarf Sink Like Stone | Submerge fully (eyes under water) → you sink even while paddling. |
| Hellborn | "Infernal Vigor" (renamed from Nether Vigor): passives fire near fire/lava/arid land — no Nether needed. |
| Character screen | O → species row shows your kit as green "+"/red "−" lines under the name — diet and mob-kin rules included. |
| Skill tooltip | Skills screen → hover a skill name → tooltip shows what it does **and how to train it**. |
| Skill detail UI | Character screen (O) → Skills → a skill → panel shows milestone effects; resize the window small → panel clips, nothing overflows. |
| Icons | Skills use vanilla item art (bow, pickaxe, piston, axe…); morph forms use spawn-egg art; cards/HUD rows show real icons — no text placeholders. |
| Key hint | Pick a species → chat prints the Character/Ability keybind names once. |

## Admin-side checks

- `/lifepath` bare → version + a pointer to `/lifepath help`.
- `/lifepath help` → lists every subcommand.
- `/lifepath reload` after a datapack edit → per-domain OK/FAIL lines.
- `/lifepath character reset <player> confirm` — refuses without `confirm`.
