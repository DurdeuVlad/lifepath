"""Lifepath CurseForge branding candidates — procedural pixel-art composer.

Builds candidate icon + banner PNGs from the mod's own shipped 16x glyph
textures and a hand-drawn 5x7 pixel font. Everything is on a strict integer
pixel grid with nearest-neighbor upscales — no AI raster artifacts possible.

Run:  python art/branding/make_candidates.py
Out:  art/branding/candidates/*.png  (non-destructive; shipped finals untouched)
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
TEX = ROOT / "common/src/main/resources/assets/lifepath/textures/gui/ability"
OUT = Path(__file__).resolve().parent / "candidates"
OUT.mkdir(exist_ok=True)

# ---- palette (small fixed set, dark navy like the shipped icon) ----
BG      = (22, 27, 46)      # deep navy
BORDER  = (43, 52, 84)      # navy edge
PANEL   = (31, 38, 63)      # glyph tile
STONE_H = (184, 192, 204)   # stone highlight
STONE_M = (130, 140, 156)   # stone mid
STONE_D = (85, 94, 110)     # stone shadow
CREAM   = (240, 230, 200)   # wordmark
CYAN    = (127, 233, 255)   # waypoint glow
CYAN_D  = (63, 168, 201)
GOLD    = (232, 193, 90)

# ---- 5x7 pixel font ----
FONT = {
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    " ": ["00000"] * 7,
}


def draw_word(d: ImageDraw.ImageDraw, x: int, y: int, text: str,
              scale: int, color) -> int:
    """Draw text on an integer pixel grid; returns drawn width in px."""
    cx = x
    for ch in text:
        rows = FONT[ch]
        for ry, row in enumerate(rows):
            for rx, v in enumerate(row):
                if v == "1":
                    d.rectangle([cx + rx * scale, y + ry * scale,
                                 cx + rx * scale + scale - 1,
                                 y + ry * scale + scale - 1], fill=color)
        cx += 6 * scale
    return cx - x - scale  # minus trailing gap


def glyph_tile(base: Image.Image, name: str, x: int, y: int, tile: int) -> None:
    """Paste a 16px ability glyph centered on a dark tile."""
    g = Image.open(TEX / f"{name}.png").convert("RGBA")
    d = ImageDraw.Draw(base)
    d.rectangle([x, y, x + tile - 1, y + tile - 1], fill=PANEL)
    d.rectangle([x, y, x + tile - 1, y + tile - 1], outline=BORDER)
    inner = tile - 16
    g = g.resize((inner, inner), Image.NEAREST)
    base.paste(g, (x + 8, y + 8), g)


def stair(d: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int) -> None:
    """One stone step: top highlight, mid face, bottom shadow."""
    d.rectangle([x, y, x + w - 1, y + 2], fill=STONE_H)
    d.rectangle([x, y + 3, x + w - 1, y + h - 3], fill=STONE_M)
    d.rectangle([x, y + h - 2, x + w - 1, y + h - 1], fill=STONE_D)


def waypoint(d: ImageDraw.ImageDraw, cx: int, cy: int) -> None:
    """Glowing diamond waypoint node (the 'goal' at the path's end)."""
    for r, c in ((4, CYAN_D), (3, CYAN), (1, (255, 255, 255))):
        for dy in range(-r, r + 1):
            w = r - abs(dy)
            d.rectangle([cx - w, cy + dy, cx + w, cy + dy], fill=c)


def frame(d: ImageDraw.ImageDraw, w: int, h: int, inset: int = 8) -> None:
    d.rectangle([inset, inset, w - inset - 1, h - inset - 1], outline=BORDER)
    d.rectangle([inset + 2, inset + 2, w - inset - 3, h - inset - 3],
                outline=(30, 36, 60))


# ---------------------------------------------------------------- icons
def icon_stairs(path: Path) -> None:
    """Ascending stone stairway + cyan waypoint — refined take on the
    shipped concept. Drawn at 128, upscaled x4."""
    im = Image.new("RGB", (128, 128), BG)
    d = ImageDraw.Draw(im)
    frame(d, 128, 128, inset=3)
    for i in range(6):
        stair(d, 14 + i * 15, 88 - i * 12, 30, 15)
    waypoint(d, 106, 26)
    im.resize((512, 512), Image.NEAREST).save(path)


def icon_glyph(path: Path, glyph: str) -> None:
    """Single hero glyph on navy, dashed 'path' of stones under it."""
    im = Image.new("RGB", (512, 512), BG)
    d = ImageDraw.Draw(im)
    frame(d, 512, 512)
    g = Image.open(TEX / f"{glyph}.png").convert("RGBA")
    g = g.resize((256, 256), Image.NEAREST)
    im.paste(g, (128, 96), g)
    # dashed path trail below the glyph
    x = 168
    while x < 352:
        d.rectangle([x, 396, x + 16, 408], fill=STONE_M)
        x += 32
    waypoint(d, 368, 402)
    im.save(path)


# ---------------------------------------------------------------- banners
def word_w(text: str, scale: int) -> int:
    return len(text) * 6 * scale - scale


def banner_tiles(path: Path) -> None:
    """Wordmark + 3 glyph tiles + dashed path underline."""
    im = Image.new("RGB", (1200, 400), BG)
    d = ImageDraw.Draw(im)
    frame(d, 1200, 400)
    scale = 14
    x = 100
    y = (400 - 7 * scale) // 2 - 20
    draw_word(d, x, y, "LIFEPATH", scale, CREAM)
    # dashed path under the wordmark
    px = x + 10
    while px < x + word_w("LIFEPATH", scale) - 40:
        d.rectangle([px, 300, px + 12, 310], fill=STONE_M)
        px += 26
    waypoint(d, x + word_w("LIFEPATH", scale) - 14, 304)
    for i, g in enumerate(("celestial_radiance", "goliath_warcry",
                          "air_leap")):
        glyph_tile(im, g, 884 + i * 92, 156, 88)
    im.save(path)


def banner_stairs(path: Path) -> None:
    """Wordmark left, mini stairway scene right."""
    im = Image.new("RGB", (1200, 400), BG)
    d = ImageDraw.Draw(im)
    frame(d, 1200, 400)
    scale = 16
    x = 60
    y = (400 - 7 * scale) // 2
    draw_word(d, x, y, "LIFEPATH", scale, CREAM)
    # stairway ascending right side (5 steps, clear of the wordmark)
    for i in range(5):
        stair(d, 880 + i * 56, 312 - i * 48, 112, 40)
    waypoint(d, 1150, 80)
    im.save(path)


if __name__ == "__main__":
    icon_stairs(OUT / "icon_a_stairs.png")
    banner_tiles(OUT / "banner_a_tiles.png")
    banner_stairs(OUT / "banner_b_stairs.png")
    # 64px downscale check for each icon candidate
    for p in sorted(OUT.glob("icon_*.png")):
        if p.stem.endswith("_64"):
            continue
        Image.open(p).resize((64, 64), Image.NEAREST).save(
            p.with_name(p.stem + "_64.png"))
    print("wrote", *[p.name for p in sorted(OUT.glob("*.png"))], sep="\n  ")
