# Lifepath Admin Command Reference (M10-3)

All commands live under `/lifepath`. **ADMIN_PERMISSION = 2** — every
subtree below requires permission level 2 (op / command block / console),
except `version`. **Players never need commands**: species and
specialization selection happens in the selection screen (auto-opens on
join while species is unset; reopen anytime from the Character screen's
Choose button). The screen enforces `selection`/`unlock` rules through
server-validated requests — `set` commands below are the admin override.

| Command | Access | Effect |
|---|---|---|
| `version` | player | Print mod + data versions. |
| `reload` | admin | Re-run content + config reload; per-domain OK/FAIL summary, then the `content validation` report. |
| `character inspect <player>` | admin | Full character dump: species, specialization, skills + progress, traits, conditions, attunements, unlocks, resources, cooldowns, action signatures. |
| `character reset <player> confirm` | admin | Wipe the target's character to defaults. `confirm` is required — this is the destructive path. |
| `species get <player>` | admin | Show the target's species. |
| `species set <player> <id>` | admin | Force a species, bypassing `selection` rules (the admin override path — the player path is the selection screen). |
| `specialization get <player>` | admin | Show the target's specialization. |
| `specialization set <player> <id>` | admin | Force a specialization. |
| `condition get <player>` | admin | List held conditions + stage/progress. |
| `condition add <player> <id>` | admin | Grant a condition (fails if unknown/already held). |
| `condition remove <player> <id>` | admin | Cure a held condition. |
| `condition stage <player> <id> <n>` | admin | Set a held condition's stage index directly. |
| `attunement get / add / remove` | admin | Same get/grant/revoke shape for attunements. |
| `unlock get / add / remove` | admin | Query / grant / revoke unlock-definition ids (grants the definition's gated content). |
| `debug character <player>` | admin | Verbose dump incl. active XP modifiers and diminishing-returns window hits. |
| `debug ability <player> <ability>` | admin | Owned? trigger, cooldown, cost state for one ability. |
| `debug skill <player> <skill>` | admin | Level, XP, decay projection, diminishing-return hits for one skill. |
| `cooldown clear <player> <ability>` | admin | Force-clear one cooldown. |

Notes:

- `<id>` arguments are identifiers (`namespace:path`); `set`/`add`/`remove`
  target a *player*.
- `species set` (admin) ignores unlock enforcement; the selection screen is
  the path that enforces `selection`/`unlocked` rules (`hidden` species
  surface in the picker once the player holds their unlock).
- `unlock add <id>` takes an **unlock definition** id, not a content id — the
  definition's `content` list is what lands in `data.unlocks()`.
- `condition stage`'s `<n>` is clamped to the definition's stage range.
- `character reset <player>` without the `confirm` literal is refused — the
  destructive form is always `reset <player> confirm`.

See `DATAPACK_API.md` for what unlocks/conditions/attunements grant.
