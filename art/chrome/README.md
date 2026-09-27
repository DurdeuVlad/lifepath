# Lifepath UI chrome kit (staging only)

These PNGs are visual staging references for the Lifepath GUI. They are not
packaged engine textures and are intentionally larger than the final draw
surfaces so the border, corner, and state treatment can be reviewed at GUI
scales 1–4. The kit uses vanilla-adjacent dark panels, iron/bone trim, stepped
resource colors, and icon/state differences so meaning is not communicated by
color alone.

The source board was generated as pixel-art reference art and each crop was
reduced with nearest-neighbor sampling and a limited palette. Keep the visible
corner and edge pixels when turning a crop into a production texture; do not
smooth or blur the edges.

## Assets and intended 9-slice insets

All crops are approximately 348×305 or 348×306 PNGs. Insets below are
starting points in source pixels, not engine constants; scale them with the
final texture if a crop is trimmed.

| File | Intended use | Suggested inset / cap | State or readability note |
|---|---|---:|---|
| `window_background.png` | Repeating panel/window interior | 24 px | Dark texture remains legible without a heavy vignette. |
| `frame_border.png` | Window/card frame | 32 px | Preserve the four stepped corners; the center edges can stretch. |
| `tab_icons.png` | Tab strip and four tab glyphs | 12 px | Keep selected/unselected icon silhouettes distinct, not color-only. |
| `button_states.png` | Button normal / hover / disabled samples | 16 px | Use the border/value change plus the state glyph treatment. |
| `slot_states.png` | Slot normal / hover / disabled samples | 12 px | Preserve the inset well and the visibly different focus state. |
| `progress_xp_fill.png` | Progress or XP fill | 6 px horizontal caps | Stretch the center fill; retain the dark empty track. |
| `protected_floor_marker.png` | Protected-floor marker | 12 px | The cyan four-corner marker is an icon shape, not a color swatch. |
| `decay_state_indicator.png` | Decay-state indicator | 12 px | Four discrete skull/state silhouettes; do not use a smooth ramp. |
| `resource_bar_texture.png` | Generic resource bar, including Iceborn temperature | 6 px horizontal caps | Uses discrete cold/neutral/hot steps from blue through amber. |
| `unlock_acquired_marker.png` | Locked/unlocked or acquired marker | 12 px | Closed/open lock silhouettes provide non-color signaling. |
| `species_card_frame.png` | Species card frame | 28 px | Stretch the center panel; keep the iron corners and top medallion. |
| `specialization_card_frame.png` | Specialization card frame | 28 px | Stretch the center panel; gold accents distinguish the card family. |

## Production handoff

These assets are deliberately kept under `art/chrome/` and are not referenced
by code or resource data. Before packaging, crop or redraw only the needed
subregions into the target GUI atlas/texture layout and verify the actual
screen at GUI scales 1–4. Keep the dark panel contrast, stepped shading,
top-left highlights, and icon/state silhouettes from the references.
