#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable-nodpi" / "ic_launcher_foreground_bitmap.webp"

source = Image.open(SOURCE).convert("RGBA")

# The repository source image is the user's supplied finished icon crop.
# Adaptive icons must not use that whole rounded square as their foreground,
# because launchers apply another mask and crop it. Extract only the central
# card/read-focus symbol and keep it inside Android's adaptive safe zone.
symbol_region = source.crop((210, 200, 1030, 1015))

hsv = symbol_region.convert("HSV")
sat = hsv.getchannel("S")
luma = ImageOps.grayscale(symbol_region)

def sat_alpha(v: int) -> int:
    if v <= 6:
        return 0
    if v >= 32:
        return 255
    return int((v - 6) * 255 / 26)

def dark_alpha(v: int) -> int:
    if v >= 210:
        return 0
    if v <= 110:
        return 255
    return int((210 - v) * 255 / 100)

alpha = ImageChops.lighter(sat.point(sat_alpha), luma.point(dark_alpha))
alpha = alpha.filter(ImageFilter.GaussianBlur(0.6)).point(lambda v: 0 if v < 8 else v)
symbol_region.putalpha(alpha)
bbox = symbol_region.getbbox()
if not bbox:
    raise RuntimeError("Could not extract launcher symbol")
symbol = symbol_region.crop(bbox)

def fit_symbol(size: int, ratio: float) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    max_dim = int(size * ratio)
    scale = min(max_dim / symbol.width, max_dim / symbol.height)
    resized = symbol.resize(
        (max(1, round(symbol.width * scale)), max(1, round(symbol.height * scale))),
        Image.Resampling.LANCZOS,
    )
    canvas.alpha_composite(resized, ((size - resized.width) // 2, (size - resized.height) // 2))
    return canvas

# 432 px maps cleanly to a 108 dp adaptive layer at xxxhdpi. The visible
# symbol occupies only 66% of the layer, keeping all important detail inside
# the adaptive icon safe area for squircle, circle and other launcher masks.
adaptive = fit_symbol(432, 0.66)
drawable = RES / "drawable-nodpi"
drawable.mkdir(parents=True, exist_ok=True)
adaptive.save(drawable / "ic_launcher_symbol.png", optimize=True)

mono = Image.new("RGBA", adaptive.size, (255, 255, 255, 0))
mono.putalpha(adaptive.getchannel("A"))
mono.save(drawable / "ic_launcher_monochrome.png", optimize=True)

# Real raster fallback icons are included because some system surfaces and
# OEM launchers request legacy/round activity icons instead of the adaptive
# drawable. This prevents falling back to Android's generic robot icon.
density_sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
for density, size in density_sizes.items():
    folder = RES / ("mipmap-" + density)
    folder.mkdir(parents=True, exist_ok=True)

    legacy = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art_size = max(1, int(size * 0.92))
    art = source.resize((art_size, art_size), Image.Resampling.LANCZOS)
    legacy.alpha_composite(art, ((size - art_size) // 2, (size - art_size) // 2))
    legacy.save(folder / "ic_launcher.png", optimize=True)

    round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(round_icon)
    draw.ellipse((1, 1, size - 2, size - 2), fill=(255, 255, 255, 255))
    round_symbol = fit_symbol(size, 0.62)
    round_icon.alpha_composite(round_symbol)
    round_icon.save(folder / "ic_launcher_round.png", optimize=True)

# Remove the old anydpi bitmap XML fallback. Its indirect bitmap reference was
# unnecessary and some OEM icon loaders are more reliable with real mipmaps.
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    p = RES / "mipmap-anydpi" / name
    if p.exists():
        p.unlink()
