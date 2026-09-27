# Release gallery — `1.0.0-beta.1`

Real in-game captures from the shipping build (`lifepath-1.0.0-beta.1`,
Minecraft 1.21.1 + Fabric Loader 0.16.10 + Fabric API 0.116.17+1.21.1).
Taken on a locally staged Creative world; character state was set through the
game's own `/lifepath` commands — **no mockups, no edited pixels**. Framebuffer
sizes vary (854×480–1904×1001); all are unmodified F2 captures.

| File | Shows | Provenance |
|---|---|---|
| `01-species-selection.png` | Species selection — the player-facing `/lifepath species choose` suggestion list (all 15 species). There is no dedicated selection GUI in this build; commands with tab-completion are the selection surface. | Chat input, suggestion overlay |
| `02-specialization-selection.png` | Specialization assignment — `/lifepath specialization set <player>` id list (13 specs). Admin-facing in this build; a player-facing picker is pending. | Chat input, suggestion overlay |
| `03-character-screen.png` | Character hub (`C`): Iceborn + Blacksmith hero rows with icons, focus line, conditions/attunements/traits sections, the new **Abilities** list — actives clickable (Frost Walk), passives tagged — plus the bound-key hint. | Dev client, in-world |
| `04-skills-screen.png` | Skills overview (`C` → Skills): all 13 skills with icons, levels, rank names, and per-skill XP progress tracks. | Dev client, in-world |
| `05-skill-detail.png` | Skill detail: Engineering at level 15 — Novice rank, aptitude, XP to next level, next milestone, decay protection timer, "how to improve" line. | Dev client, in-world |
| `06-ability-frost-walk.png` | In-world ability activation: Frost Walk fired on a water pool — surface frozen to frosted ice — with the ability's 116s cooldown counting down in the HUD. | Dev client, in-world |
| `07-hud-temperature.png` | Iceborn temperature resource: band name ("Warming") + value rendered over the world — the M6-3 resource HUD row with its icon. | Dev client, in-world |
| `08-hud-cooldown.png` | Cooldown row + resource band together: Frost Walk on cooldown (113s remaining, icon + name + seconds) while the Iceborn is Overheating (87). | Dev client, in-world |

## Not pictured — honest gaps

- **Overgeared + Smithing integration shot**: blocked upstream — no Overgeared
  build exists for Fabric 1.21.1 (Fabric builds ship 1.20.1; the 1.21.1 builds
  are NeoForge). See `docs/COMPAT.md`. We do not claim the integration.
- **Species/specialization pickers**: selection is command-driven in this build
  (items 01–02 show the real surface); GUI pickers are future work, not
  shipped, so they are not screenshotted.

## How the activation shot was staged

`gamemode creative` → `fill` a 9×9 water pool → `C` → click `Frost Walk` in
the Abilities list (or press `G` unbound for the server-side auto-pick) → the
pool surface freezes and the 120 s cooldown row appears. The teleport framings
used `tp` only to position the camera; the freeze itself is the shipped
`lifepath:freeze_water` action on real water blocks.
