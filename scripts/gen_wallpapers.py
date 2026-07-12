#!/usr/bin/env python3
"""Generate the procedural Win95 wallpapers (teal weave, starfield) at phone
resolution, and shrink the artwork wallpapers with palette quantization.

Usage:
  .venv/bin/python scripts/gen_wallpapers.py app/src/main/res/drawable-nodpi
"""

import random
import sys
from pathlib import Path

from PIL import Image

WIDTH, HEIGHT = 1440, 3200
TEAL = (0, 128, 128)


def teal() -> Image.Image:
    """The classic teal desktop, with the subtle woven speckle texture."""
    rng = random.Random(95)
    img = Image.new("RGB", (WIDTH, HEIGHT), TEAL)
    px = img.load()
    darker = (0, 112, 112)
    lighter = (16, 140, 140)
    for _ in range(WIDTH * HEIGHT // 12):
        x, y = rng.randrange(WIDTH), rng.randrange(HEIGHT)
        px[x, y] = darker if rng.random() < 0.6 else lighter
    return img


def stars() -> Image.Image:
    """Starfield-screensaver night sky."""
    rng = random.Random(95)
    img = Image.new("RGB", (WIDTH, HEIGHT), (0, 0, 0))
    px = img.load()
    for _ in range(1400):
        x, y = rng.randrange(WIDTH), rng.randrange(HEIGHT)
        v = rng.choice((255, 255, 200, 160, 120))
        px[x, y] = (v, v, v)
        if v == 255 and rng.random() < 0.25:  # a few bright cross-shaped stars
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < WIDTH and 0 <= y + dy < HEIGHT:
                    px[x + dx, y + dy] = (160, 160, 160)
    return img


def compress(path: Path) -> None:
    """Quantize a wallpaper to a 256-color palette; the Win95 look survives."""
    img = Image.open(path).convert("RGB")
    before = path.stat().st_size
    img.quantize(colors=256, dither=Image.Dither.FLOYDSTEINBERG).save(path, optimize=True)
    print(f"{path.name}: {before // 1024}KB -> {path.stat().st_size // 1024}KB")


def main() -> int:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/drawable-nodpi")
    teal().save(out / "wall_teal.png")
    stars().save(out / "wall_stars.png")
    print(f"generated wall_teal.png and wall_stars.png at {WIDTH}x{HEIGHT}")
    for name in sorted(out.glob("wall_*.png")):
        compress(name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
