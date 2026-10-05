# M12-4 Icon Coverage Audit & Polish Gate (#127)

Evidence for the milestone-exit gate: coverage matrix, anti-slop review,
consistency sheets, accessibility re-check, and the text-reduction measure.
Feeds LP-REL-005 (#58, gallery) and LP-REL-010 (#63, go/no-go).

Verdict: **PASS** — 129/129 shipped defs resolve to final 16×16 PNG art.
Zero unexplained gaps. Two asset defects found during audit and fixed in
this pass (see Findings).

## 1. Coverage matrix

Enumerated every UI-rendered content id by reading `data/lifepath/<domain>/*.json`
and resolving each `icon` ref through the `IconRef` normalization rules
(`<domain>/<name>` → `assets/lifepath/textures/gui/<domain>/<name>.png`).

| Domain | Defs | Final icon | Placeholder | Absent |
|---|---|---|---|---|
| species | 15 | 15 | 0 | 0 |
| specialization | 13 | 13 | 0 | 0 |
| skill | 13 | 13 | 0 | 0 |
| ability | 79 | 79 | 0 | 0 |
| attunement | 3 | 3 | 0 | 0 |
| condition | 2 | 2 | 0 | 0 |
| resource | 4 | 4 | 0 | 0 |
| **Total** | **129** | **129** | **0** | **0** |

Per-def table: `art/audit/_matrix.md` (generated, 129 rows — every row
`FINAL`).

**Excused-absent ids (runtime-only, by design):**

- Trait section rows: trait ids resolve through `entry()`/`iconRef()` to
  ability defs, so ability-owned traits render their ability icon. A trait
  id with no content def renders text-only — excused: no trait icon domain
  exists in the visual contract; the row still renders its name.
- `placeholder/<domain>.png` files remain engine fallback for hypothetical
  icon-less defs — none ship icon-less.

## 2. Anti-slop review (LP-REL-007 checklist, per asset)

Automated checks on all 129 files + eyeball review of per-domain contact
sheets (`art/audit/sheet_*.png`, 4× NEAREST upscale):

| Check | Result |
|---|---|
| Exactly 16×16 PNG | PASS 129/129 (programmatic) |
| Transparent bg on object icons / dark tile on badges | PASS — badges (attunement/condition/resource) all sit on `#17171F`-family tiles with themed borders |
| Reads at ~10 px | PASS on sheets at 1× preview; every icon keeps a single dominant silhouette |
| No text/letters/pseudo-glyphs | PASS — no glyph-noise in any tile |
| Palette ≤3 hue families + neutral | PASS — palettes are tight (e.g. regenerated `human.png` = 5 colors incl. alpha) |
| No gradients/blur/glow/painterly | PASS after fixes — see Findings |
| Distinctness within domain | PASS — silhouettes vary (tools, orbs, wings, vials); the three `sylvian_arid_*` potions share a vial template with different fluid color — flagged P3, family reads as intentional potion-set |
| Not a recolor / item-atlas copy | PASS — no sprite is a straight vanilla item copy |

## 3. Findings & fixes in this pass

1. **`species/human.png` — REJECTED & REGENERATED.** Was a black blob on a
   hot-magenta tile (chroma-key failure; read as a missing-texture sprite).
   Regenerated via image-gen: neutral warm-grey/bone bust silhouette,
   transparent bg, 5 opaque palette colors, nearest-neighbor downscale.
2. **Fuchsia keying-residue speckles** on 7 species icons (amphibian 1px,
   anima 15px, automaton 2px, celestial 23px, dragonborn 2px, enderian 4px,
   sylvian 1px) — surgically inpainted to neighbor median. Post-fix scan:
   zero saturated-fuchsia pixels in the species domain.
3. **False-positive note:** `anima_pack_heart`, `earth_stone_skin`,
   `sylvian_arid_{nausea,slow}` contain red-pink pixels that matched the
   fuchsia hue band — inspected at 10×: all are intentional potion-fluid
   color, not contamination. Left unchanged.
4. **M16-4 skill re-read (Beta-8 pass): all 13 skill icons REPLACED.**
   The previous set were recolored weapon/shield sprites — `cooking`,
   `farming`, `scholarship`, `woodcutting` et al. rendered as tinted
   swords that did not read as their skills at 16×16. Every skill icon
   is now a purpose-drawn silhouette: bow+arrow (archery), winged boot
   (athletics), steaming pot (cooking), shield (defence), cog
   (engineering), wheat (farming), fish (fishing), berry cluster
   (foraging), paw print (hunting), pickaxe (mining), open book
   (scholarship), anvil (smithing), axe (woodcutting). Verified on a
   4×-zoom contact sheet and a native 1× strip
   (`art/audit/_zoom_skills_final.png`, `_zoom_skills_1x.png`) — every
   icon reads as its skill at render size. Still a static-asset check;
   live client capture remains a #58 item.

## 4. Consistency

Per-domain sheets in `art/audit/`:

- `sheet_species.png` (15) — emblem family; mix of orb-sigils and object
  silhouettes, reads coherently post-fix.
- `sheet_spec.png` (13) — trade-tool objects, consistent weight/detail.
- `sheet_skill.png` (13) — activity sigils on transparent bg.
- `sheet_ability.png` (79) — largest family; detail level consistent,
  species-prefixed abilities carry their species palette per the motif map.
- `sheet_badges.png` (9) — attunement+condition+resource badges, uniform
  dark-tile + themed-border convention.
- `sheet_chrome.png` (12) — staging-only chrome kit (`art/chrome/`), not
  packaged.

## 5. Accessibility re-check

- No icon-only signaling: every rendered icon sits beside a drawn
  `Text.literal(e.name())` — verified at all draw sites in
  `CharacterScreen`, `SkillsScreen`, `SkillDetailScreen`, `LifepathHud`.
- No color-only state: resource bands still render name + numeric value;
  cooldown/state rows keep text labels.
- Species description is hover-tooltip (was always-on) — content remains
  reachable; names are always-on.
- Icons draw at fixed sizes (16px hero rows, ~10px rows) — GUI scale 1–4
  keeps pixel-art NEAREST scaling; no fractional-pixel layout introduced.

## 6. Text-reduction measure

CharacterScreen (`c83683a^` vs `HEAD`):

| Metric | Before | After |
|---|---|---|
| Inline species description | ~29 words avg rendered as wrapped paragraph | 0 (moved to hover tooltip; still reachable) |
| Always-on labels | species/spec/section names | unchanged — all names retained |
| Icons on default view | 0 | hero rows + every section row |

Net: **~29 fewer always-on words** on the primary surface, all information
retained (names always-on, description on hover). Icon count on default
view went 0 → every content row. The milestone promise "less text, more
icons" is delivered modestly on the character screen; HUD/Skills keep
names by a11y contract (icons augment, never replace).

## 7. Honest limitations

- **No live in-game screenshots**: the audit ran against code paths and
  rendered sprites, not a running client — `runClient` requires an
  interactive MC session. GUI-scale confidence is from code inspection
  (fixed draw sizes) + sheet previews. #58 (gallery screenshots) remains
  the vehicle for live captures.
- `ClientIcons` placeholder cache keys by icon id while fallback depends
  on caller domain — P3 carryover from M12 review; no shipped def triggers
  cross-domain sharing.
- Three `sylvian_arid_*` potion icons share a vial template (different
  fluid) — flagged P3 consistency, not a blocker.

## 8. Tests performed

- Programmatic coverage audit (above): 129/129 resolve, 0 missing, 0
  placeholder refs.
- Format assert: every icon is PNG, exactly 16×16, RGBA.
- Palette scans: fuchsia-residue (fixed), synthesized-PIL-color scan: 0
  survivors.
- `./gradlew build` green at `da76feb`; 344 tests, 0 failures.
