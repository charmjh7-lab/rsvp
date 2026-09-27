#!/usr/bin/env python3
from math import hypot
from pathlib import Path

from PIL import Image, ImageChops, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
SOURCE = RES / "drawable-nodpi" / "ic_launcher_source.webp"
OUTPUT = RES / "drawable-nodpi" / "ic_launcher_foreground_bitmap.webp"

# Android adaptive icons use a 108 dp layer. At xxxhdpi, 432 px maps exactly
# to 108 dp. Keep the entire mark, including the rear blue card, inside the
# 66 dp safe circle rather than sizing from the front dark card alone.
CANVAS_PX = 432
TARGET_MAX_PX = 212
SAFE_RADIUS_PX = 132.0

source = Image.open(SOURCE).convert("RGBA")
if source.size != (CANVAS_PX, CANVAS_PX):
    raise RuntimeError(
        f"Expected {CANVAS_PX}x{CANVAS_PX} launcher source, got {source.size}"
    )

# The source is the previous finished icon: a white rounded-square plate plus
# the real card mark. Build alpha only from the colored/dark mark so the white
# plate and its shadow are not baked into the adaptive foreground.
hsv = source.convert("HSV")
saturation = hsv.getchannel("S")
luma = ImageOps.grayscale(source)


def saturation_alpha(value: int) -> int:
    if value <= 10:
        return 0
    if value >= 38:
        return 255
    return int((value - 10) * 255 / 28)


def darkness_alpha(value: int) -> int:
    if value >= 210:
        return 0
    if value <= 155:
        return 255
    return int((210 - value) * 255 / 55)


alpha = ImageChops.lighter(
    saturation.point(saturation_alpha),
    luma.point(darkness_alpha),
)

# Exclude the old outer rounded-square edge/shadow before calculating bounds.
margin = round(CANVAS_PX * 0.14)
central_alpha = Image.new("L", source.size, 0)
central_alpha.paste(
    alpha.crop((margin, margin, CANVAS_PX - margin, CANVAS_PX - margin)),
    (margin, margin),
)
central_alpha = central_alpha.point(lambda value: 0 if value < 5 else value)

mark = source.copy()
mark.putalpha(central_alpha)
bounds = central_alpha.getbbox()
if not bounds:
    raise RuntimeError("Could not extract launcher mark from source image")

# IMPORTANT: bounds include every visible part, especially the rear blue card.
mark = mark.crop(bounds)
scale = min(TARGET_MAX_PX / mark.width, TARGET_MAX_PX / mark.height)
size = (
    max(1, round(mark.width * scale)),
    max(1, round(mark.height * scale)),
)
mark = mark.resize(size, Image.Resampling.LANCZOS)

foreground = Image.new("RGBA", (CANVAS_PX, CANVAS_PX), (0, 0, 0, 0))
foreground.alpha_composite(
    mark,
    ((CANVAS_PX - mark.width) // 2, (CANVAS_PX - mark.height) // 2),
)

# Fail generation if any significant pixel leaves Android's conservative
# 66 dp safe circle (33 dp radius). This prevents future accidental zooming.
alpha = foreground.getchannel("A")
pixels = alpha.load()
center = CANVAS_PX / 2
max_radius = max(
    hypot(x - center, y - center)
    for y in range(CANVAS_PX)
    for x in range(CANVAS_PX)
    if pixels[x, y] >= 64
)
if max_radius > SAFE_RADIUS_PX:
    raise RuntimeError(
        f"Foreground exceeds adaptive safe area: {max_radius:.2f}px > "
        f"{SAFE_RADIUS_PX:.2f}px"
    )

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
foreground.save(OUTPUT, "WEBP", lossless=True, method=6)

visible_bounds = alpha.getbbox()
print(
    "Generated adaptive foreground:",
    f"source_bounds={bounds}",
    f"visible_bounds={visible_bounds}",
    f"visible_size={visible_bounds[2]-visible_bounds[0]}x"
    f"{visible_bounds[3]-visible_bounds[1]}px",
    f"max_radius={max_radius:.2f}px ({max_radius / 4:.2f}dp)",
)
