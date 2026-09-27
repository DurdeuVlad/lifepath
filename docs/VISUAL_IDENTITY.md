# Lifepath Visual Identity Guide

> **Audience:** the image-generating agent that produces `lifepath` art —
> in-game icons (AI-ART-01/02/03) *and* release/branding assets (LP-REL-003,
> -004, -005) — plus reviewers of that art (LP-REL-007 anti-slop gate), and
> datapack authors adding `icon` fields to custom content.
>
> **Purpose:** every asset must read as Minecraft-native and as one coherent
> family. This file is the binding contract — the AI-ART and LP-REL issues
> reject violations of it.
>
> **Two asset classes, one identity.** Part I (§1–§7) is the binding spec
> for *in-game sprites* (16×16 pixel art, strict). Part II (§8–§10) is the
> direction for *promotional/release art* (looser medium, same soul).

---

## 1. What this mod draws

Lifepath renders content icons in three places:

| Surface | Draw size | Consumers |
|---|---|---|
| Skills list / detail, character screen hero rows | 16×16 | skill, species, specialization |
| Character screen sections, skill bonuses | 8–10 px (16×16 sprite scaled) | condition, attunement, trait-abilities, milestone abilities |
| HUD rows (resources, cooldowns, states) | 10 px (16×16 sprite scaled) | resource, ability, condition |

Every icon is authored at **16×16 PNG** and scaled down at draw time — so a
sprite must survive being read at ~10 px. Test perception at 10 px, not at
authoring zoom.

Texture root: `assets/lifepath/textures/gui/<domain>/<name>.png`
(`domain` ∈ `species | specialization | skill | ability | condition |
attunement | resource`). Content defs reference icons via the normalized
ref `"<domain>/<name>"` → `lifepath:textures/gui/<domain>/<name>.png`.

## 2. Hard rules (violations = reject)

1. **16×16 PNG, power-of-two, no padding.** Single sprite per file — no
   sprite sheets.
2. **No text, letters, or pseudo-text.** Icons are read cross-language;
   decorative glyph-noise is AI slop.
3. **Silhouette-first.** The shape must be identifiable at 10 px against the
   dark panel `#C0101015`. Prefer one strong object over many small ones.
4. **Palette discipline.** 2–3 hue families per sprite plus a neutral
   (iron-grey `#9DA5AD`, bone `#E8E0D0`). No full-spectrum rainbows.
5. **Minecraft-style lighting.** Light comes from top-left; shadows sit
   bottom-right. Outlines are 1 px, darker shade of the fill color — never
   pure black `#000` and never soft/airbrushed.
6. **Pixel-art cells.** Edges are axis-aligned or 45° steps; no smooth
   diagonals, anti-aliasing, gradients, blur, glow, or bloom.
7. **Transparent background** for object icons; the engine draws its own
   panel. Do not bake vignettes or backdrops into object icons.
8. **Distinctness within a domain.** Two skills must not differ only by
   hue swap — vary silhouette, then color.
9. **No photorealism, no painterly shading, no lens/depth effects.**
10. **Badge glyphs** (conditions/attunements) may use a 1 px themed border
    on a dark tile (`#17171F` fill ≈ HUD panel) so tiny states read framed —
    matching the shipped placeholders' convention.

## 3. Palette anchors

| Role | Hex | Use |
|---|---|---|
| Panel dark | `#10101C`–`#17171F` | badge tile fill |
| Panel edge | `#3A3A44` | tile border |
| Accent cyan | `#55FFFF` | magic/celestial only — sparingly |
| Resource blue | `#58B0D8` | resource bars/symbols |
| State amber | `#FFC857` | conditions/warnings |
| Iron neutral | `#9DA5AD` | tools, frames, armor motifs |
| Bone neutral | `#E8E0D0` | parchment, skull, light hits |

Species/attunement/condition motifs take their thematic hue (fire,
frost, verdant, void…) — see §5.

## 4. Domain conventions

| Domain | Composition | Notes |
|---|---|---|
| `species` | Portrait-ish glyph: face/profile sigil of the species' motif | Reads at hero size 16px; this is the identity anchor |
| `specialization` | Tool/marker of the trade, centered, slight 45° tilt allowed | Cross with vanilla tool silhouettes where the spec mirrors one (miner ≈ pickaxe but NOT an item copy — different shading/colors) |
| `skill` | Activity sigil | Same rule — evocative, not an inventory-item photo |
| `ability` | Effect sigil: burst, aura, claw, wave, spark | Passive traits read calmer (single object); actives read energetic (rays/motion ticks) |
| `condition` | Badge on dark tile w/ 1 px border | Hex/curse sigil inside tile |
| `attunement` | Badge: element glyph inside tile | air/earth/lightning ship now |
| `resource` | Meter sigil | temperature=thermometer/gauge feel; blood=drop; load=weight; phantom_form=wisp |

## 5. Motif map for shipped content

Generating agent: use this as the prompt seed per file. Hue words map to the
palette anchors + species thematics; keep each sprite to 2–3 hue families.

**Species** (`species/<id>.png`): human = neutral face sigil (iron/bone);
sylvian = leaf+antler, forest green+bone; dwarf = short helm+beard, stone
grey+amber; iceborn = shard crown, ice blue+bone; amphibian = frog eye,
green+cyan; anima = beast paw, warm brown+amber; automaton = gear face,
iron+brass; celestial = halo star, gold+white; dragonborn = horned maw,
red+ember; enderian = ender eye, purple+black; goliath = massive fist,
stone+bronze; hellborn = imp horn+flame, crimson+black; phantom = wisp
veil, pale cyan+grey; phoenix = rising wing, orange+gold; undead = skull
half, bone+rot-green.

**Specializations** (`specialization/<id>.png`): blacksmith = hammer+anvil
edge; cook = ladle+steam; engineer = gear+wrench; explorer = compass rose;
farmer = hoe+wheat ear; fisherman = hook+wave; herbalist = mortar+leaf;
hunter = bow+arrow tip; laborer = raised hammer; lumberjack = axe+bark chip;
miner = pick+ore spark; scholar = open book+quill; wanderer = boot+path
stitch.

**Skills** (`skill/<id>.png`): archery = fletched arrow; athletics = winged
foot; cooking = pot+flame; defence = shield kite; engineering = gear+bolt;
farming = sprout; fishing = fish+line arc; foraging = basket+berries;
hunting = crossed antler+spear; mining = pick+gem chip; scholarship =
tome+sigil; smithing = anvil+spark; woodcutting = axe+log ring.

**Attunements** (`attunement/<id>.png`): air = swirl gust (pale cyan);
earth = crag block (umber); lightning = forked bolt (electric yellow).

**Conditions** (`condition/<id>.png`): lycanthropy = moon+claw (bone+amber);
vampirism = fang+drop (crimson+bone).

**Resources** (`resource/<id>.png`): blood = drop vial (crimson); load =
weight hook (iron); phantom_form = wisp curl (pale cyan); temperature =
thermometer (blue→amber gradient *steps*, not smooth).

**Abilities** (`ability/<id>.png`): 79 shipped ids — derive the palette from
the owning species prefix (`iceborn_*` → ice blues, `hellborn_*` →
crimson/ember, `sylvian_*` → verdant, etc.) and the motif from the verb:
`leap/drift/step` → motion sigil; `breath/smoke/discharge` → burst;
`frame/skin/bones` → armor/ward; `sense/sight/eyes` → eye glyph;
`regen/mend/bloom` → growth sigil; `frenzy/fury/warcry` → jagged marks;
`sickness/vulnerability/aversion` → cracked/wilted mark. Standalone utility
abilities (no species prefix) take neutral iron+bone with one accent.

## 6. Placeholders & fallback

Shipped `textures/gui/placeholder/<domain>.png` (dark tile + `?` glyph) are
**engine fallback**, not content art — never ship them as a real icon and
never restyle them. Missing declared icons fall back to them automatically
(warn-once in the log), so art can land incrementally without breaking
builds.

## 7. Icon acceptance checklist (in-game sprites)

For every delivered sprite file:

- [ ] Exactly 16×16 PNG; transparent bg for object icons; tile bg only for badges
- [ ] Reads at 10 px: silhouette identifiable, no detail mush
- [ ] Zero text/letters/pseudo-glyphs; zero AI artifacts (extra lines, merged shapes, fake shading blobs)
- [ ] Palette ≤ 3 hue families + neutral; consistent light direction (top-left)
- [ ] No smooth gradients/blur/glow — pixel-art cells only
- [ ] File lands at `assets/lifepath/textures/gui/<domain>/<id>.png` and the def's `icon` field resolves to it
- [ ] Not a recolor of another icon in the same domain
- [ ] Distinct from vanilla item sprites it might resemble (no item-atlas copy-paste)

## 8. Release/branding art direction (Part II)

Softer rules than in-game sprites — these are promo images, not engine
textures — but they must still feel like Lifepath, not generic fantasy.

- **Logo/title treatment.** The word `LIFEPATH` in a chunky, Minecraft-echo
  display treatment (blocky letterforms or vanilla-style flat pixel font),
  optionally with one motif swap per release (e.g., a leaf in the "P", a
  gear for the "A") — never more than one decorated letter. No bevelled
  metal, no glowing runes, no fantasy-serif fonts.
- **Screenshot framing (release gallery).** In-game footage over renders.
  Rule of thirds; the HUD visible in at least one shot so the mod's actual
  face is on the store page. Daytime or stylized night — never pure-black
  caves. UI open in at most half the shots.
- **Environment style.** Real Minecraft worlds only (no HD renders, no
  Unreal-style lighting). Vanilla shaders acceptable if the block grid is
  still legible. Scenes should show progression-relevant contexts: a forge,
  a dig site, a night field.
- **Character style.** Vanilla player models only — blocky Steve-proportion
  bodies. Species-read via subtle overlays/tints the mod actually applies;
  never bespoke high-poly characters.
- **CurseForge banner.** Product name + one row of the shipped icon glyphs
  is the safest composition. No stock art, no text paragraphs, no fake UI.

## 9. Reject-list (unacceptable tropes — any asset)

- Generic fantasy warrior/mage/wizard renders — Lifepath has no them
- High-fantasy book/candle/parchment clip-art for skills
- Muscle-bound anime characters, chibi proportions
- Lens flares, particle soup, painterly brushwork, HDR bloom
- Any visible generated text (except the wordmark itself)
- Anatomically-impossible hands/faces/limbs
- Cluttered "epic battle" compositions for a progression mod
- Copied Mojang marketing art, item-atlas sprites, or other mods' assets

## 10. Universal review checklist

Run on **every** asset (icons *and* promo art) before acceptance — this is
the LP-REL-007 checklist, verbatim:

- Is the subject immediately clear?
- Is the composition simple enough?
- Does it look like Minecraft or compatible Minecraft promo art?
- Are all visible items/blocks plausible?
- Is any text readable and intentional?
- Any malformed hands/faces/limbs?
- Free of meaningless detail clutter?
- Survives cropping/scaling?

A "generated" provenance is not a defense — fail one item, regenerate.

---

Milestone gate (M12-4, #127): coverage audit measures words-on-screen
before/after and confirms every *declared* icon resolves to a real texture
(no placeholder remains for shipped content).
