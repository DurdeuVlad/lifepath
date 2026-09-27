# Deliverable Manifest: AI-ART-03 (Issue #66)

## Deliverable A: Engine-wired badge/sigil icons

| ID | File | Status | Checklist Self-Check | Notes |
|---|---|---|---|---|
| `air` | `attunement/air.png` | ✅ Done | 16x16 PNG, reads at 10px, pale cyan swirl, #17171F tile, no text, pixel art style. | Generated and downscaled via nearest-neighbor to preserve chunky pixel-art look. |
| `earth` | `attunement/earth.png` | ✅ Done | 16x16 PNG, reads at 10px, umber crag, #17171F tile, no text, pixel art style. | Generated and downscaled via nearest-neighbor. |
| `lightning` | `attunement/lightning.png` | ✅ Done | 16x16 PNG, reads at 10px, electric yellow bolt, #17171F tile, no text. | Generated and downscaled via nearest-neighbor. |
| `lycanthropy` | `condition/lycanthropy.png` | ✅ Done | Valid 16x16 PNG; moon+claw glyph; bone/amber palette; #17171F tile with themed 1px border; no text; hard pixel cells. | Generated source art, then nearest-neighbor reduced and palette-stepped. |
| `vampirism` | `condition/vampirism.png` | ✅ Done | Valid 16x16 PNG; fang+drop glyph; crimson/bone palette; #17171F tile with themed 1px border; no text; hard pixel cells. | Generated source art, then nearest-neighbor reduced and palette-stepped. |
| `blood` | `resource/blood.png` | ✅ Done | Valid 16x16 PNG; drop-vial glyph; crimson palette; #17171F tile with themed 1px border; no text; hard pixel cells. | Generated source art, then nearest-neighbor reduced and palette-stepped. |
| `load` | `resource/load.png` | ✅ Done | Valid 16x16 PNG; weight-hook glyph; iron-grey palette; #17171F tile with themed 1px border; no text; hard pixel cells. | Generated source art, then nearest-neighbor reduced and palette-stepped. |
| `phantom_form` | `resource/phantom_form.png` | ✅ Done | Valid 16x16 PNG; wisp-curl glyph; pale-cyan palette; #17171F tile with themed 1px border; no text; hard pixel cells. | Generated source art, then nearest-neighbor reduced and palette-stepped. |
| `temperature` | `resource/temperature.png` | ✅ Done | Valid 16x16 PNG; thermometer glyph; discrete blue/teal/amber steps; #17171F tile with themed 1px border; no text; hard pixel cells. | Generated source art, then nearest-neighbor reduced and palette-stepped; no smooth gradient. |

> Scope note: this issue explicitly defines all six remaining files as badge/sigil
> icons and requires the opaque `#17171F` tile convention for the set. The four
> `resource/` files therefore intentionally use the framed tile here, overriding
> the generic transparent-object resource convention in the broader guide.

## Deliverable B: UI Chrome Kit

| Asset | Status | Notes |
|---|---|---|
| `art/chrome/` | ✅ Done | Staging-only chrome kit with 12 generously-sized PNG crops and `README.md` describing intended 9-slice insets. |

### Blockers / Action Items
- No known blockers remain for the requested Deliverables A and B.
- Deliverable A was generated with the image generation tool, then reduced with nearest-neighbor sampling and discrete palette mapping; every target file was independently validated for PNG format and exact 16x16 dimensions.
- Deliverable B remains staging-only under `art/chrome/`; no code, data, placeholder textures, or existing PNGs were modified.
