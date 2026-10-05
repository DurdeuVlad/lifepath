# M17 — Phoenix design record (D8 resolution)

**Status:** implemented in Beta 8 — records the as-built decisions for
owner sign-off (M17-1 deliverable).
**Date:** Beta 8 implementation pass. **Milestone:** M17 (plan:
`docs/BETA8_MILESTONE_PLAN.md`).

## Context

The reference mod (Reincarnation Origins: Phoenix) reverts the player to a
weakened "Rebirth Form" on death and restores "True Form" after a timer.
Lifepath owns the machinery: staged `condition` defs with
`advance_after_seconds`, death-triggered acquisition (`type:death` rule +
`ServerPlayer.die` seam), and a refreshed `grant_flight` action revoked by
the passive sweep when its conditions stop passing.

## Form table

| | True Form | Rebirth Form (`phoenix_rebirth` stage 0) |
|---|---|---|
| Fire immunity | `phoenix_fire_blood` (fire ×0) | kept |
| Heal while burning | `phoenix_warmth` | kept |
| Water hurts | `phoenix_quench` | kept |
| Flight | `phoenix_wings` (flame-gated, below) | **revoked** — grant conditions fail |
| Debuffs | none | `phoenix_rebirth_frailty`: Weakness I, Slowness I, scale ×0.8 (refreshed every 40t) |
| Actives | `phoenix_rise` (300s), `phoenix_flare` (45s) | kept |
| Duration | — | 120s, then `advance_after_seconds` → stage 1 |

Stage 1 (`restored`) carries no abilities and is purely a marker: the
condition persists after True Form returns as a visible record that a
rebirth happened. Accepted for now — if HUD clutter becomes a complaint, a
terminal-stage auto-cure would be a small `ConditionService` addition.

## Rebirth trigger semantics

**Real death, respawn in Rebirth Form** (not lethal-hit conversion):
`ServerPlayerMixin` fires `ConditionService.onDeath` at `die`'s TAIL —
vanilla death, death screen, and respawn all proceed normally — and the
condition persists across respawn via the character attachment. Dying
*again* mid-molt re-acquires the condition at stage 0 and restarts the
120s timer.

*Rejected:* lethal-hit conversion at 1 HP (no death screen) — heavier
machinery (cancel `actuallyHurt`, fake state), no shipped payoff; the
reference mod's own loop is death → weakened respawn.

## Flight — cost model (D8)

Creative-style `mayfly` via a **refreshed grant** (`grant_flight`, 80-tick
keep-alive marker), charged by a new resource so it isn't free travel —
the Frost Walk lesson:

- `lifepath:flame` (Inner Flame): 0–100, default 100, `regen_per_second`
  2.5 while the species owns it.
- `phoenix_wings` grants flight only while **not** in Rebirth stage 0 AND
  `flame ≥ 20`.
- `phoenix_flight_drain` (`is_flying` gate) burns **−4 flame/s while
  airborne** — grounded time is free, and takeoff is instant whenever
  flame is sufficient. Regen keeps ticking while flying, so airborne net
  drain is −1.5/s.
- Effective budget: ~53s continuous flight from full (100→20 at −1.5/s);
  from the guttering edge (~0–19) it's ~0–8s grounded to clear the gate;
  full recharge from empty ≈ 40s.
- When flame gutters below 20 the grant stops refreshing — the 80-tick
  marker means ~≤4s of glide tail before `reconcileFlight` strips
  `mayfly`, so flame management mid-air matters (no fall-damage
  immunity is granted).
- Grant lapse is not instant by design: a short warning glide rather
  than a hard cut.

*Rejected:* elytra-style glide — requires movement-state machinery the
vocabulary doesn't have; the resource cost achieves the same economy.
*Rejected:* cooldown-burst flight — a lapsing grant mid-air is lethal
without warning; drain-then-revoke already has the gentler tail.

## Unlock story

`phoenix` is `visibility: hidden`, `selection: unlocked`. The
`phoenix_contract` unlock def grants it when the player **consumes a
Totem of Undying** — you offer an extra life to bind yourself to the
first Phoenix's line.

## Related decisions landed in this pass

- **D5 (diet UX):** hard-block implemented — `ItemMixin` wraps the
  `canEat` check in `Item.use`, `ItemUtilsMixin` cancels
  `startUsingInstantly` for denied FOOD stacks; denied food can't be
  consumed at all (use returns `fail`), with the existing
  `lifepath.diet.denied` actionbar line. The `Player.eat` zero-nutrition
  wrap remains as a backstop for direct `eat()` calls.
- **D9 (undead helmet exemption):** not implemented — `undead_sun_burn`
  burns under `daylight` + `exposed_to_sky` regardless of headgear.
  Still open; the exemption is a one-line equipment check if accepted.
