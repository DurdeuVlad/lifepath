#!/usr/bin/env python3
"""Lifepath GUI icon generator (Beta-10 icon pass).

Two jobs:

1. MORPH icons — real vanilla spawn-egg art: extracts the shared
   spawn_egg/spawn_egg_overlay sprites from the Minecraft client jar and
   tints them with the per-entity colors baked into Items.<clinit> (the
   same math the game uses — SpawnEggItem.getColor).  Output goes to
   assets/lifepath/textures/gui/morph/<animal>.png; the morph_form defs
   reference them via the "morph/<animal>" shorthand.

2. FLAT GLYPHS — clean two-tone silhouette icons for ability + species
   domains.  The shipped PNGs were procedural noise; these are authored
   16x16 string art tinted per species group so the whole set reads as one
   icon family.  Run after editing GLYPHS/COLORS; previews print to stdout.

Usage:  python tools/icons/make_icons.py [--jar <client.jar>]
"""

import os, sys, zipfile
from PIL import Image

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GUI = os.path.join(REPO, "common/src/main/resources/assets/lifepath/textures/gui")
DEFAULT_JAR = os.path.expandvars(
    r"%USERPROFILE%\.gradle\caches\neoformruntime\artifacts\minecraft_1.21.1_client.jar")

# ---------------------------------------------------------------- eggs --

# (entity, primary, secondary) — values lifted from Items.<clinit>
# SpawnEggItem constructor args in the 1.21.1 client jar.
EGG_COLORS = {
    "cat":        (0xEFC88E, 0x957256),
    "fox":        (0xD5B69F, 0xCC6920),
    "goat":       (0xA5947C, 0x55493E),
    "panda":      (0xE7E7E7, 0x1B1B22),
    "polar_bear": (0xEEEDE0, 0xD5D6CD),
    "rabbit":     (0x995F40, 0x734831),
    "sheep":      (0xE7E7E7, 0xFFB5B5),
    "wolf":       (0xD7D3D3, 0xCEAF96),
}


def tint(img, rgb):
    """Vanilla-style multiply tint on an RGBA layer."""
    r, g, b = (rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255)
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            pr, pg, pb, pa = px[x, y]
            if pa:
                px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out


def make_egg_icons(jar_path):
    os.makedirs(os.path.join(GUI, "morph"), exist_ok=True)
    with zipfile.ZipFile(jar_path) as z:
        base = Image.open(z.open(
            "assets/minecraft/textures/item/spawn_egg.png")).convert("RGBA")
        over = Image.open(z.open(
            "assets/minecraft/textures/item/spawn_egg_overlay.png")).convert("RGBA")
    for animal, (primary, secondary) in EGG_COLORS.items():
        egg = tint(base, primary)
        egg.alpha_composite(tint(over, secondary))
        out = os.path.join(GUI, "morph", animal + ".png")
        egg.save(out)
        print("morph/" + animal + ".png  #%06x/#%06x" % (primary, secondary))


# -------------------------------------------------------------- glyphs --

# Glyph legend:  '#' base color · '-' dark (auto shade) · '+' light (auto)
#                'o' accent color (second palette entry)

GLYPHS = {
    # --- semantic glyphs (ability domain) ---
    "gear": [
        "     ####     ",
        "     ####     ",
        "  ##########  ",
        "  ##########  ",
        " #####  ##### ",
        "######  ######",
        "#####    #####",
        "#####    #####",
        "######  ######",
        " #####  ##### ",
        "  ##########  ",
        "  ##########  ",
        "     ####     ",
        "     ####     ",
    ],
    "bolt": [
        "      #####   ",
        "     #####    ",
        "    #####     ",
        "   #####      ",
        "  ##########  ",
        "  #########   ",
        "     #####    ",
        "    #####     ",
        "   #####      ",
        "  #####       ",
        "  ####        ",
        "  ###         ",
        "  ##          ",
        "  #           ",
    ],
    "frame": [
        " ############ ",
        "##############",
        "###        ###",
        "###        ###",
        "###        ###",
        "###        ###",
        "###        ###",
        "###        ###",
        "###        ###",
        "###        ###",
        "###        ###",
        "###        ###",
        "##############",
        " ############ ",
    ],
    "drip": [
        "      ##      ",
        "     ####     ",
        "     ####     ",
        "    ######    ",
        "    ######    ",
        "   ########   ",
        "   ########   ",
        "  ##########  ",
        "  ##########  ",
        "  ##########  ",
        "   ########   ",
        "    ######    ",
        "      ##      ",
    ],
    "jaw": [
        "  #   ##   #  ",
        " ###  ##  ### ",
        " ###  ##  ### ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " #####  ##### ",
        "  ###    ###  ",
        "  ##      ##  ",
        "  #        #  ",
    ],
    "breath": [
        "    #####     ",
        "   #######    ",
        "  ###   ###   ",
        "  ##     ##   ",
        "  ##     ##   ",
        "  ###   ###   ",
        "   #######    ",
        "    #####     ",
        "       ##     ",
        "      ### ##  ",
        "     ###   ## ",
        "    ###     ##",
        "    ###   ### ",
        "     #######  ",
    ],
    "fish": [
        "     ####     ",
        "   ########   ",
        "  ##########  ",
        " ########### #",
        "############ #",
        "############ #",
        " ########### #",
        "  ##########  ",
        "   ########   ",
        "     ####     ",
    ],
    "wave": [
        "    ##        ",
        "   ####       ",
        " ###### ###   ",
        "########  ##  ",
        "#######    ###",
        "######     ##",
        "        ##    ",
        "    ##        ",
        "   ####       ",
        " ###### ###   ",
        "########  ##  ",
        "#######    ###",
        "######     ##",
    ],
    "sun": [
        "       ##     ",
        "  ##  #### ## ",
        "   ## #### ## ",
        "    ########  ",
        "  ## ######## ",
        " #####o ##### ",
        "##### oo #####",
        "##### oo #####",
        " #####o ##### ",
        "  ######## ## ",
        "   #######    ",
        "  ## #### ##  ",
        " ##  ####  ## ",
        "     ##       ",
    ],
    "paw": [
        "   ##     ##  ",
        "  ####   #### ",
        "  ####   #### ",
        "   ##     ##  ",
        "    ######    ",
        "   ########   ",
        "  ##########  ",
        "  ##########  ",
        "  ##########  ",
        "   ########   ",
        "    ######    ",
    ],
    "heart": [
        "  ###   ###   ",
        " ##### #####  ",
        "############# ",
        "############# ",
        "############# ",
        " ###########  ",
        "  #########   ",
        "   #######    ",
        "    #####     ",
        "     ###      ",
        "      #       ",
    ],
    "star": [
        "       #      ",
        "      ###     ",
        "      ###     ",
        "  #  #####  # ",
        " ###########  ",
        "############# ",
        "  #########   ",
        "   #######    ",
        "  #########   ",
        "  ###   ###   ",
        " ###     ###  ",
        " #         #  ",
    ],
    "eye": [
        "    ######    ",
        "  ##########  ",
        " #####  ##### ",
        "#### oo ######",
        "#### oo ##### ",
        "#####  #####  ",
        "  ##########  ",
        "    ######    ",
    ],
    "bone": [
        " ##        ## ",
        "####      ####",
        " ####    #### ",
        "   ########   ",
        "    ######    ",
        "     ####     ",
        "     ####     ",
        "    ######    ",
        "   ########   ",
        " ####    #### ",
        "####      ####",
        " ##        ## ",
    ],
    "gem": [
        "   ########   ",
        "  #++++++++#  ",
        " #++######++# ",
        "#+###    ###+#",
        "#+##      ##+#",
        "#+##      ##+#",
        " #+##    ##+# ",
        "  #+######+#  ",
        "   #+####+#   ",
        "    #+##+#    ",
        "     #++#     ",
        "      ##      ",
    ],
    "mountain": [
        "       ##     ",
        "      ####    ",
        "     ##+###   ",
        "    ###+####  ",
        "   ####  ###  ",
        "  ####    ### ",
        " #####    ####",
        "#####     ####",
        "####    ######",
        "###   ########",
        "##  ##########",
        "# ############",
    ],
    "shield": [
        " ############ ",
        "##############",
        "##############",
        "##############",
        "##############",
        "##############",
        " ######### ## ",
        "  ##########  ",
        "   ########   ",
        "    ######    ",
        "     ####     ",
        "      ##      ",
    ],
    "boot": [
        "  ####        ",
        "  ####        ",
        "  ####        ",
        "  ####        ",
        "  ####        ",
        "  ####  ###   ",
        "  ##########  ",
        "  ########### ",
        " ############ ",
        " ############ ",
        " ##### ###### ",
        " ####   ##### ",
    ],
    "hammer": [
        "  ########    ",
        " ##########   ",
        "##########    ",
        " ##########   ",
        "  ####### ##  ",
        "       ## ##  ",
        "      ##  ##  ",
        "     ##    ## ",
        "    ##     ## ",
        "   ##      ## ",
        "  ##       ## ",
        "  ##        # ",
    ],
    "horn": [
        "    ##########",
        "   ########## ",
        "  ##########  ",
        " #########    ",
        "#########     ",
        "########      ",
        "#######       ",
        "######        ",
        "#####         ",
        " ####         ",
        "  ##          ",
    ],
    "snowflake": [
        "  ##  ##  ##  ",
        "   ## ## ##   ",
        " ##  ##### ## ",
        "  ### ### ### ",
        "   ########   ",
        "############# ",
        "############# ",
        "   ########   ",
        "  ### ### ### ",
        " ##  ##### ## ",
        "   ## ## ##   ",
        "  ##  ##  ##  ",
    ],
    "moon": [
        "      #####   ",
        "    ########  ",
        "   ########## ",
        "  #######     ",
        " ######       ",
        "######        ",
        "######        ",
        "######        ",
        " ######       ",
        "  #######     ",
        "   ########## ",
        "    ########  ",
        "      #####   ",
    ],
    "ghost": [
        "     #####    ",
        "   #########  ",
        "  ########### ",
        "  ##o#####o## ",
        "  ########### ",
        "  ########### ",
        "  ########### ",
        "  ########### ",
        "  ########### ",
        "  ## ### ## # ",
        "  #  #  #  #  ",
    ],
    "flame": [
        "      #       ",
        "     ##       ",
        "     ### #    ",
        "    ##### #   ",
        "    ######    ",
        "   ########   ",
        "   ########   ",
        "  ##########  ",
        "  ####++####  ",
        "  ###++++###  ",
        "   ##++++##   ",
        "    ##++##    ",
        "     ####     ",
        "      ##      ",
    ],
    "leaf": [
        "         ###  ",
        "       ###### ",
        "      ####### ",
        "     ######## ",
        "    ######### ",
        "   #########  ",
        "   ########   ",
        "  ########    ",
        "  #######     ",
        "   #####      ",
        "   ###        ",
        "  ###         ",
        " ###          ",
        " ##           ",
    ],
    "skull": [
        "    ######    ",
        "  ##########  ",
        " ############ ",
        " ##oo####oo## ",
        " ##oo####oo## ",
        " ############ ",
        "  ####  ####  ",
        "   ########   ",
        "   # ## # #   ",
        "   ########   ",
        "    ######    ",
    ],
    "swirl": [
        "   ########   ",
        "  ###     ### ",
        " ###       ###",
        " ##   ####   #",
        "##   ######  #",
        "##   ##  ##   ",
        "##   ##  #    ",
        " ##  ##       ",
        " ##      #####",
        "  ###  ###### ",
        "   ########   ",
    ],
    "anchor": [
        "      ##      ",
        "     ####     ",
        "     ####     ",
        "      ##      ",
        "      ##      ",
        "  ##  ##  ##  ",
        " ###  ##  ### ",
        " ###  ##  ### ",
        " ###  ##  ### ",
        "  #########   ",
        "   #######    ",
        "      ##      ",
    ],
    "cloud": [
        "    #####     ",
        "   #######    ",
        "  ##########  ",
        " ############ ",
        " ############ ",
        "  ######  ##  ",
        "     ##    ## ",
        "    ##        ",
        "   ##     ##  ",
        "    ##    ##  ",
        "     ## ##    ",
        "      ##      ",
    ],
    "wing": [
        " ##           ",
        " ####         ",
        " ######       ",
        "  ########    ",
        "   ########## ",
        "    ##########",
        "     #########",
        "      ####### ",
        "       #####  ",
        "        ###   ",
        "         #    ",
    ],
    "arrow_up": [
        "       #      ",
        "      ###     ",
        "     #####    ",
        "    #######   ",
        "   #########  ",
        "  ########### ",
        "      ###     ",
        "      ###     ",
        "      ###     ",
        "      ###     ",
        "      ###     ",
        "      ###     ",
    ],
    "hourglass": [
        " #############",
        " #############",
        "  ########### ",
        "   #########  ",
        "    #######   ",
        "     #####    ",
        "     #####    ",
        "    #######   ",
        "   #########  ",
        "  ########### ",
        " #############",
        " #############",
    ],
    "pick": [
        "     ######## ",
        "   ###########",
        "  ####     ###",
        " ####     ##  ",
        " ####    ##   ",
        "  ##   ##     ",
        "      ##      ",
        "     ##       ",
        "    ##        ",
        "   ##         ",
        "  ##          ",
        "  ##          ",
    ],
    "barrier": [
        "  ##########  ",
        " ####    #### ",
        "###  ##    ###",
        "###  ####  ###",
        "##   ####   ##",
        "##    ##    ##",
        "##          ##",
        "###        ###",
        " ####    #### ",
        "  ##########  ",
    ],
}

# Semantic glyph + palette per ability (prefix → (glyph, base, accent)).
# Palette: base fill; '-' shade auto-darkens; 'o' uses accent.
def _c(hexstr):
    return tuple(int(hexstr[i:i+2], 16) for i in (0, 2, 4))

SPECIES_COLORS = {
    "automaton": _c("9FA4A8"), "amphibian": _c("58A05C"),
    "anima": _c("D98A3D"), "celestial": _c("E8D98A"),
    "dragonborn": _c("C25034"), "dwarf": _c("A9835B"),
    "enderian": _c("8E5FB8"), "goliath": _c("7A7A85"),
    "hellborn": _c("B03A2E"), "iceborn": _c("7EC8E3"),
    "phantom": _c("9FC4C4"), "phoenix": _c("E8802A"),
    "sylvian": _c("5E9A4E"), "undead": _c("7A8A6E"),
    "vampirism": _c("8E1B1B"), "lycanthropy": _c("7B5C3E"),
    "lightning": _c("E8D84A"), "earth": _c("8A6B46"),
    "air": _c("CFE8E0"),
}
DEFAULT_COLOR = _c("B0B0B0")
ACCENT = _c("222831")

ABILITY_GLYPH = {
    "automaton_devour_iron": "jaw", "automaton_iron_frame": "frame",
    "automaton_no_breath": "breath", "automaton_overclock": "bolt",
    "automaton_rust": "drip",
    "amphibian_gills": "fish", "amphibian_dry_skin": "sun",
    "amphibian_slick_swimmer": "wave", "amphibian_tidal_mend": "wave",
    "anima_beast_soothe": "heart", "anima_hollow_bones": "bone",
    "anima_morph": "paw", "anima_pack_heart": "paw",
    "celestial_exile": "star", "celestial_radiance": "sun",
    "celestial_smite": "bolt", "celestial_starfall": "star",
    "deepvein_sense_i": "gem",
    "dragonborn_breath": "flame", "dragonborn_fire_blood": "flame",
    "dragonborn_frost_vulnerability": "snowflake",
    "dragonborn_molten_core": "flame",
    "dwarf_compact_frame": "frame", "dwarf_sink_stone": "anchor",
    "dwarf_stoneform": "mountain", "dwarf_sturdy_frame": "frame",
    "dwarf_subterranean": "pick",
    "earth_anchor": "anchor", "earth_stone_skin": "shield",
    "enderian_blink": "swirl", "enderian_pearl_blood": "gem",
    "enderian_void_attunement": "swirl", "enderian_water_burn": "drip",
    "forge_mastery_i": "hammer",
    "goliath_big_target": "frame", "goliath_large_frame": "frame",
    "goliath_thunderous_step": "boot", "goliath_warcry": "horn",
    "hellborn_immolate": "flame", "hellborn_infernal_heat": "flame",
    "hellborn_nether_vigor": "flame", "hellborn_water_aversion": "drip",
    "hold_the_line": "shield",
    "iceborn_arid_heat": "sun", "iceborn_cold_biome": "snowflake",
    "iceborn_coolant": "snowflake", "iceborn_frostbite": "snowflake",
    "iceborn_heat_sources": "flame", "iceborn_ice_nearby": "snowflake",
    "iceborn_lava_nearby": "flame", "iceborn_overheat": "sun",
    "lightning_discharge": "bolt", "lightning_grounded": "bolt",
    "lightning_storm_charge": "cloud",
    "lycanthropy_feral_senses": "eye", "lycanthropy_full_moon": "moon",
    "lycanthropy_moon_fury": "moon",
    "patient_waters_i": "fish", "bountiful_harvest_i": "leaf",
    "phantom_drift": "ghost", "phantom_insubstantial": "ghost",
    "phantom_phase": "ghost", "phantom_sunflare": "sun",
    "phoenix_fire_blood": "flame", "phoenix_flare": "flame",
    "phoenix_flight_drain": "wing", "phoenix_quench": "drip",
    "phoenix_rebirth_frailty": "star", "phoenix_rise": "wing",
    "phoenix_warmth": "sun", "phoenix_wings": "wing",
    "sylvian_arid_nausea": "sun", "sylvian_arid_slow": "sun",
    "sylvian_forest_affinity": "leaf", "sylvian_grass_walker": "leaf",
    "sylvian_nature_purification": "leaf",
    "sylvian_necrotic_vulnerability": "skull",
    "sylvian_verdant_bloom": "leaf",
    "undead_death_sight": "eye", "undead_night_strength": "moon",
    "undead_rot_touch": "skull", "undead_sun_burn": "sun",
    "vampirism_apex_regen": "heart", "vampirism_blood_frenzy": "jaw",
    "vampirism_drain": "drip", "vampirism_night_eyes": "eye",
    "vampirism_sun_sickness": "sun",
    "human_fortune": "star", "human_resolve": "heart",
    "undead_night_eyes": "eye",
    "air_featherweight": "wing", "air_leap": "arrow_up",
}

# Species icons — bespoke silhouettes, species-colored.
SPECIES_GLYPH = {
    "human": [
        "     #####    ",
        "    #######   ",
        "    #######   ",
        "    #######   ",
        "     #####    ",
        "      ###     ",
        "   #########  ",
        "  ########### ",
        "  ########### ",
        "  ########### ",
        "  ########### ",
        "  ########### ",
        "  ###     ### ",
        "  ###     ### ",
    ],
    "dwarf": [
        "    ######    ",
        "   ########   ",
        "   ########   ",
        "   ## ## ##   ",
        "   ########   ",
        "  ##########  ",
        " ###########  ",
        " ############ ",
        " ############ ",
        " ############ ",
        "  ### ## ###  ",
        "  ##  ##  ##  ",
    ],
    "automaton": [
        "      ##      ",
        "   ########   ",
        "  ##########  ",
        "  ##o####o##  ",
        "  ##########  ",
        "  ##########  ",
        "  ##########  ",
        "   ########   ",
        "    ######    ",
    ],
    "amphibian": [
        "  ###    ###  ",
        " #####  ##### ",
        "##o##    ##o##",
        "######  ######",
        " ##############",
        " ##############",
        " ############ ",
        " ## ###### ## ",
        " ############ ",
        " ############ ",
    ],
    "anima": list(GLYPHS["paw"]),
    "celestial": [
        "       #      ",
        "      ###     ",
        "      ###     ",
        " #   #####  # ",
        "############ ",
        "  #########   ",
        "    #####     ",
        "   #######    ",
        "  ###   ###   ",
        " ###     ###  ",
    ],
    "dragonborn": [
        "  #        #  ",
        "  ##      ##  ",
        "  ###    ###  ",
        "  ##########  ",
        " ###o####o### ",
        " ############ ",
        " ############ ",
        "  ###### ###  ",
        "  ######   ## ",
        "   #####      ",
    ],
    "enderian": [
        "  ##########  ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ##+######+## ",
        " ##+######+## ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
    ],
    "goliath": [
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
        " ############ ",
    ],
    "hellborn": [
        " ##        ## ",
        " ###      ### ",
        " ####    #### ",
        " ##############",
        " ############ ",
        " ##o######o## ",
        " ############ ",
        " ############ ",
        "  ### ## ###  ",
        "   ##    ##   ",
    ],
    "iceborn": list(GLYPHS["snowflake"]),
    "phantom": list(GLYPHS["ghost"]),
    "phoenix": [
        "  ##       ## ",
        "  ###     ### ",
        "  ####   #### ",
        "  #####o##### ",
        "   #########  ",
        "    #######   ",
        "   #########  ",
        "  ########### ",
        " ## ####### ##",
        "##   #####   #",
        "#     ###     ",
    ],
    "sylvian": [
        "     ####     ",
        "    ######    ",
        "    ##o##     ",
        "    ######    ",
        "   ########   ",
        "  ##########  ",
        " ##########   ",
        "  #######     ",
        "   #####      ",
        "    ###       ",
        "   ###        ",
        "  ###         ",
    ],
    "undead": list(GLYPHS["skull"]),
}


def _shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb)


def render_glyph(rows, base, accent, pad=16):
    """String art → RGBA image padded to 16x16, top-centered."""
    h = len(rows)
    w = max(len(r) for r in rows)
    img = Image.new("RGBA", (pad, pad), (0, 0, 0, 0))
    ox = (pad - w) // 2
    oy = max(0, (pad - h) // 2)
    dark = _shade(base, 0.62)
    light = _shade(base, 1.3)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ' ' or y + oy >= pad or x + ox >= pad:
                continue
            rgb = base if ch == '#' else dark if ch == '-' else \
                light if ch == '+' else accent if ch == 'o' else None
            if rgb:
                img.putpixel((x + ox, y + oy), rgb + (255,))
    return img


def make_flat_icons():
    import glob, json
    # Abilities: glyph per ABILITY_GLYPH map, palette per name prefix.
    for f in glob.glob(os.path.join(
            REPO, "common/src/main/resources/data/lifepath/ability/*.json")):
        name = os.path.basename(f)[:-5]
        d = json.load(open(f, encoding="utf-8"))
        icon = d.get("icon", "")
        if not icon.startswith("ability/"):
            continue
        glyph_name = ABILITY_GLYPH.get(name)
        prefix = name.split("_")[0]
        base = SPECIES_COLORS.get(prefix, DEFAULT_COLOR)
        if glyph_name is None:
            glyph_name = "star"   # honest fallback — named glyph, not noise
            print("  ! no glyph for", name, "-> star")
        img = render_glyph(GLYPHS[glyph_name], base, ACCENT)
        out = os.path.join(GUI, "ability", name + ".png")
        img.save(out)
    # Species silhouettes.
    for name, rows in SPECIES_GLYPH.items():
        img = render_glyph(rows, SPECIES_COLORS.get(name, DEFAULT_COLOR),
                           ACCENT)
        img.save(os.path.join(GUI, "species", name + ".png"))


if __name__ == "__main__":
    jar = DEFAULT_JAR
    if "--jar" in sys.argv:
        jar = sys.argv[sys.argv.index("--jar") + 1]
    make_egg_icons(jar)
    make_flat_icons()
    print("done")
