#!/usr/bin/env python3
"""Generate the dynamic calendar icons (ic_calendar_1 .. ic_calendar_31) from
the base ic_calendar artwork, for launchers that support appfilter's
<calendar prefix="..."/> mechanism.

The day number is rendered small in MS Sans Serif Bold and nearest-upscaled so
the digits stay chunky pixels like the rest of the pack.

Usage:
  .venv/bin/python scripts/gen_calendar_icons.py
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
DRAWABLE = ROOT / "app/src/main/res/drawable"
FONT = ROOT / "app/src/main/res/font/ms_sans_serif_bold.ttf"

NAVY = (0, 0, 128, 255)
WHITE = (255, 255, 255, 255)
SCALE = 5  # render small, upscale with NEAREST for crisp pixel digits


def day_number(day: int, font: ImageFont.FreeTypeFont) -> Image.Image:
    text = str(day)
    left, top, right, bottom = font.getbbox(text)
    pad = 2  # room for the 1px white halo
    small = Image.new(
        "RGBA", (right - left + 2 * pad, bottom - top + 2 * pad), (0, 0, 0, 0)
    )
    draw = ImageDraw.Draw(small)
    origin = (pad - left, pad - top)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            draw.text((origin[0] + dx, origin[1] + dy), text, font=font, fill=WHITE)
    draw.text(origin, text, font=font, fill=NAVY)
    return small.resize((small.width * SCALE, small.height * SCALE), Image.Resampling.NEAREST)


def main() -> int:
    base = Image.open(DRAWABLE / "ic_calendar.png").convert("RGBA")
    font = ImageFont.truetype(str(FONT), 16)
    # Centre of the calendar page, below the blue header bar.
    cx, cy = base.width // 2, int(base.height * 0.58)
    for day in range(1, 32):
        number = day_number(day, font)
        icon = base.copy()
        icon.alpha_composite(number, (cx - number.width // 2, cy - number.height // 2))
        icon.save(DRAWABLE / f"ic_calendar_{day}.png", optimize=True)
    print(f"generated ic_calendar_1..31 in {DRAWABLE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
