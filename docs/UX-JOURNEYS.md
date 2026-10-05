# Lifepath — UX Journey Audit (player / tester / admin)

> Method: `flux-ux` — experience contract per actor, modeled from shipped
> surfaces (screens, strings, commands, docs). Evidence is code/docs;
> anything not directly observed is marked *inference*. Live-client
> verification of flagged spots is the follow-on step.
> Date: 2026-10-07, post-Beta-8 + M18 namespace refactor.

---

## Journey 1 — Player

**User model:** vanilla Minecraft knowledge only; joins a server running
Lifepath; has never seen the mod.

| Step | Knowledge state | Evidence |
|---|---|---|
| Join | Picker auto-opens once per session while species unset (config `onboarding_auto_open`), after join screens clear | `ClientSelectionState` |
| Choose species | Card grid, icon + name + flavor + strengths/weaknesses as colored +/- lines, "What you get" detail, locked entries labeled `Requires unlock`/`Admin-only`/`Already chosen` | `docs/screenshots/selection-species.png`, `SelectionScreen` |
| Choose specialization | Same picker; explicit permanence warning: *"This choice is permanent. Only an admin can undo it."*; spec itself explains it's a focus, not a lockout | `screen.lifepath.selection.spec_warning`, `feedback.spec_assigned` |
| Skip onboarding | "Decide later" disarms the auto-open **for the session** — it re-arms on next join while species is unset | `ClientSelectionState.tickAutoOpen` |
| Play | HUD shows labeled resource bars (label+band+value, never color-only), cooldowns, condition rows; idle = absent | `LifepathHud` |
| Ability use | Character screen (O): click an ability to bind; *"G fires the first otherwise"*; ready/denied messages carry a reason (`on cooldown (Ns)`, `requires an unlock you don't hold`, `not enough resource`) | `CharacterScreen`, `feedback.lifepath.*` |
| Diet denial (Automaton) | Actionbar: *"Your body rejects that food — it gives you nothing"*; item never consumed; card disclosed the diet at pick (*"Only iron nourishes you"*) | `ItemMixin`, `automaton.json` |
| Death → rebirth (Phoenix) | `Gained: %s` condition message + HUD state row; frailty effects during the 120s molt | `ConditionService.onDeath` |

### Player findings

- **UX-P1 — Unlock grants are silent.** Right-clicking a Totem of Undying
  consumes it and grants Phoenix with no message (`UnlockService.onUseItem`
  → `fire` → `grant` only marks+syncs; `en_us.yaml` has no `unlocked`
  string). The player may never notice the hidden species surfaced.
  *Fix (landed):* `fire()` now emits `feedback.lifepath.unlocked`
  ("Unlocked: %s") resolving the content's display name (species) with
  def-name → raw-id fallback. Admin `unlock add` keeps reporting to the
  admin via its own command feedback.
- **UX-P2 — Hidden species: RESOLVED (owner decision 2026-10-07).** Secret
  for players, not for admins — admins see held unlocks via
  `/lifepath character inspect` / `unlock get` and can force-set the
  species. No player-facing hint ships.
- **UX-P3 — Post-onboarding, nothing names O/G.** Onboarding is mandatory
  (no "Decide later" escape while a pick is owed), but after the picker
  completes the only O/G hint lives inside the screen O opens.
  *Fix:* `species_assigned` feedback now also posts a one-shot chat hint
  with the player's actual keybind names (`feedback.lifepath.keys_hint`).
- **UX-P4 — Diet denial doesn't restate allowed foods.** *Fix:* diet defs
  accept an optional `deny_message` (DATAPACK_API); `ferrovore` now says
  *"only iron nourishes you"*.

## Journey 2 — Tester

**User model:** community member given beta.7 jar + "verify Beta 7 fixes";
knows GitHub exists; does not know internal issue ids.

| Step | Knowledge state | Evidence |
|---|---|---|
| Learn what to verify | README links GitHub Issues; Beta-7 reports arrived as Discord screenshot zips | `README.md:89`, `BETA8_MILESTONE_PLAN.md` |
| Find test targets | **Gap:** M14–M17 milestone plans are doc-stage; nothing player-facing maps Beta-8 fixes to testable scenarios | plan doc status line |
| Report | GitHub Issues — **no issue templates**; blank form, no repro/evidence fields | `.github/` contains only `workflows/` |
| Evidence format | `docs/evidence/m13-6/` shows the convention (screenshots per claim) — but nothing tells testers it exists | `docs/evidence/` |

### Tester findings

- **UX-T1 — No issue templates.** *Fix (landed):*
  `.github/ISSUE_TEMPLATE/bug_report.yml` — loader dropdown, version,
  repro, evidence, character setup.
- **UX-T2 — No published verification checklist.** *Fix (landed):*
  `docs/TESTING.md` maps each Beta-8 fix to a concrete verification.
  (Creating M14–M17 tracker milestones remains a separate housekeeping
  decision.)
- **UX-T3 — Evidence convention undocumented.** *Fix (landed):*
  TESTING.md codifies "repro or screenshot per claim", citing
  `docs/evidence/m13-6/`.

## Journey 3 — Admin

**User model:** runs a server, installs the jar, wants to tune content and
manage players; knows commands and datapacks.

| Step | Knowledge state | Evidence |
|---|---|---|
| Commands | `/lifepath` tree, all perm-2 except `version`; brigadier suggests subcommands | `LifepathCommands` |
| Destructive ops | `character reset` refuses without `confirm` literal | `ADMIN_COMMANDS.md` |
| Overrides | `species set`/`condition add`/`unlock add` documented as admin overrides vs picker rules, incl. definition-id caveat | `ADMIN_COMMANDS.md` |
| Content | `DATAPACK_API.md` schemas + worked example; `ABILITIES.md` vocabulary | docs |
| Validation | `/lifepath reload` → per-domain OK/FAIL + content validation report | `LifepathCommands.reload` |
| Config | Every key, default, range, reload behavior | `CONFIGURATION.md` |

### Admin findings

- **UX-A1 — Bare `/lifepath` prints only version.** *Fix (landed):*
  `/lifepath help` lists every registered subcommand + the
  docs/ADMIN_COMMANDS.md pointer; bare `/lifepath` hints at `help`.
- Strengths: override semantics documented, destructive path guarded,
  reload reports per-domain errors, config doc exhaustive.

## Priority — status

All actionable findings landed under **M19** (milestone #16):

| Finding | Fix | Issue |
|---|---|---|
| UX-P1 silent unlock | `Unlocked: %s` feedback in `UnlockService.fire` | #175 |
| UX-P3 post-onboarding keys | `keys_hint` chat line on `species_assigned` | #176 |
| UX-P4 diet denial | `deny_message` on diet defs; ferrovore customized | #176 |
| UX-A1 bare `/lifepath` | `/lifepath help` + version hint | #177 |
| UX-T1/T2/T3 tester surface | bug-report template + `docs/TESTING.md` | #178 |
| UX-P2 hidden species | **RESOLVED (decision):** secret for players, admin-visible | — |

*Live-client verification still outstanding: totem → "Unlocked: Phoenix"
chat line; species pick → keybind hint; automaton deny line.*
