# LP-REL-007 — Anti-slop review of all public assets

Scope: every image a player sees on the listing/README/gallery. Each asset was
reviewed at intended display size and at reduced size against the checklist in
`docs/VISUAL_IDENTITY.md` (no malformed anatomy, no fake/unreadable text, no
muddy focal point, no clutter, consistent Minecraft scale, no overprocessed
lighting, no AI-soup, readable at target size). "Generated" provenance is not
accepted as a defense — every asset must pass on its own pixels.

## Verdicts

| Asset | Intended size | Reduced | Verdict | Notes |
|---|---|---|---|---|
| `art/branding/lifepath_icon_512.png` | 512² | 32² | **PASS** | Path+marker motif reads cleanly; chunky pixels; palette = bone/iron/cyan on `#10101C`; no text; strong silhouette. |
| `art/branding/lifepath_icon_32.png` | 32² | 16² | **PASS** | Exact nearest-neighbor reduction of the 512 — verified pixel-exact; silhouette survives. Packaged as `assets/lifepath/icon.png` (128² ship size). |
| `art/branding/lifepath_banner.png` | 1200×400 | 600×200 | **PASS** | `LIFEPATH` wordmark in blocky caps on `#10101C`; right side is a composite of three *shipped* 16px glyphs (phoenix / smithing / explorer) at 16× nearest-neighbor — verified pixel-identical to the source textures. One focal area, no collage clutter. |
| `gallery/01-species-selection.png` | 1744×951 | thumbnail | **PASS** | Real suggestion overlay, 10 visible species ids, readable. Command surface honestly shown (no fake picker fabricated). |
| `gallery/02-specialization-selection.png` | 1744×951 | thumbnail | **PASS** | Spec id suggestion list (admin path — captioned as such). |
| `gallery/03-character-screen.png` | 1904×1001 | thumbnail | **PASS** | Full identity hub: species/spec hero icons, focus line, sections, abilities list with passive tags + click hint + key hint. No overlap, panel wraps content (fixed in this pass). |
| `gallery/04-skills-screen.png` | 1744×951 | thumbnail | **PASS** | 13 skills, icon + name + level + rank + XP track each; "click a skill for details" affordance. |
| `gallery/05-skill-detail.png` | 1744×951 | thumbnail | **PASS** | Engineering detail — level/rank/aptitude/XP/milestone/decay-timer/how-to-improve all legible. |
| `gallery/06-ability-frost-walk.png` | 1904×1001 | thumbnail | **PASS** | Real ability effect — frosted-ice ring on the staged pool + 116 s cooldown row. Chat line visible; captioned as staged Creative scene. |
| `gallery/07-hud-temperature.png` | 1904×1001 | thumbnail | **PASS** | Resource HUD over a ravine vista: "Warming 80" band + icon row. |
| `gallery/08-hud-cooldown.png` | 1904×1001 | thumbnail | **PASS** | Cooldown row ("Frost Walk 113s") + "Overheating 87" band over world view — both M12 HUD affordances in one frame. |
| README visuals section | — | — | **PASS** | No screenshots embedded in README (links to gallery dir); no fake imagery. |

## Regenerated / rejected this pass

- `species/human.png` — rejected (black blob on hot-magenta = missing-texture
  look), regenerated with real image-gen; verified binary alpha, 0 fuchsia px.
- 7 species icons — hot-magenta keying residue inpainted out (anima,
  amphibian, automaton, celestial, dragonborn, enderian, sylvian);
  re-scanned: clean.
- `ability/anima_pack_heart.png` — pink pixels reviewed, **accepted**: the
  subject is a heart; pink is the subject color, not residue.

## Known non-asset limitations (not image defects)

- Small-window (< ~300 logical px height) CharacterScreen can clip the bottom
  rows — content is taller than the viewport at GUI scale 4 / tiny windows.
  Panel now wraps content; windowed overflow is a layout constraint, not slop.
- Specialization has no player-facing picker UI yet — admin `set` is the only
  assignment path; gallery captions say so.
