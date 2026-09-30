#!/usr/bin/env python3
"""win95ify - normalize icon artwork into a consistent Win95-style icon set.

Every icon that ships in the pack should pass through this script so the
whole set shares one canvas size, one pixel grid, and one palette treatment.

Modes:
  win95ify   Full retro treatment for AI-generated or modern source images:
             downscale to a true pixel grid, quantize to the classic Win95
             palette (16 colors) or an adaptive 256-color palette, optional
             Floyd-Steinberg dithering, then upscale with hard pixels.
  normalize  Gentle mode for art that is already Win95-styled: pad to a
             square canvas and resize to the target size. No palette change.
  iconback   Generate `iconback.png`, a beveled Win95 "plaque" used by
             launchers as the backdrop for apps the pack does not theme.

Usage:
  .venv/bin/python scripts/win95ify.py win95ify  <in.png|dir> -o <outdir> [--grid 48] [--colors 16|256] [--dither]
  .venv/bin/python scripts/win95ify.py normalize <in.png|dir> -o <outdir>
  .venv/bin/python scripts/win95ify.py iconback -o app/src/main/res/drawable-nodpi

Output is always a <size>x<size> RGBA PNG (default 192x192, matching the
existing pack).
"""

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance

# The classic Windows 95 16-color VGA palette.
WIN95_PALETTE = [
    (0, 0, 0), (128, 0, 0), (0, 128, 0), (128, 128, 0),
    (0, 0, 128), (128, 0, 128), (0, 128, 128), (192, 192, 192),
    (128, 128, 128), (255, 0, 0), (0, 255, 0), (255, 255, 0),
    (0, 0, 255), (255, 0, 255), (0, 255, 255), (255, 255, 255),
]

# Win95 bevel colors for the iconback plaque.
BEVEL_LIGHT = (255, 255, 255, 255)
BEVEL_FACE = (192, 192, 192, 255)
BEVEL_SHADOW = (128, 128, 128, 255)
BEVEL_DARK = (0, 0, 0, 255)


def strip_background(img: Image.Image, tolerance: int = 40) -> Image.Image:
    """Flood-fill from the image edges, turning the near-uniform background
    transparent. For AI-generated icons, which come on a solid backdrop."""
    img = img.convert("RGBA")
    px = img.load()
    w, h = img.size
    bg = px[0, 0][:3]

    def is_bg(c) -> bool:
        return c[3] > 0 and sum(abs(a - b) for a, b in zip(c[:3], bg)) <= tolerance * 3

    from collections import deque
    queue = deque(
        [(x, y) for x in range(w) for y in (0, h - 1)]
        + [(x, y) for x in (0, w - 1) for y in range(h)]
    )
    seen = set()
    while queue:
        x, y = queue.popleft()
        if (x, y) in seen or not (0 <= x < w and 0 <= y < h):
            continue
        seen.add((x, y))
        c = px[x, y]
        if is_bg(c):
            px[x, y] = (c[0], c[1], c[2], 0)
            queue.extend([(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)])
    return img


def pad_to_square(img: Image.Image) -> Image.Image:
    """Center the image on a square transparent canvas."""
    side = max(img.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(img, ((side - img.width) // 2, (side - img.height) // 2))
    return canvas


def quantize(img: Image.Image, colors: int, dither: bool) -> Image.Image:
    """Quantize RGB content to the Win95 16-color or an adaptive palette,
    preserving the alpha channel (binarized, as real Win95 icons had)."""
    alpha = img.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
    rgb = img.convert("RGB")

    dither_mode = Image.Dither.FLOYDSTEINBERG if dither else Image.Dither.NONE
    if colors == 16:
        pal_img = Image.new("P", (1, 1))
        flat = [c for rgb_ in WIN95_PALETTE for c in rgb_]
        pal_img.putpalette(flat + flat[:3] * (256 - len(WIN95_PALETTE)))
        rgb = rgb.quantize(palette=pal_img, dither=dither_mode)
    else:
        rgb = rgb.quantize(colors=colors, dither=dither_mode)

    out = rgb.convert("RGBA")
    out.putalpha(alpha)
    return out


def win95ify(src: Image.Image, size: int, grid: int, colors: int, dither: bool, strip_bg: bool, saturation: float = 1.0) -> Image.Image:
    img = src.convert("RGBA")
    if strip_bg:
        img = strip_background(img)
    if saturation != 1.0:
        # Quantization pulls muted midtones toward gray; boosting saturation
        # first keeps the artwork's colors alive in the reduced palette.
        alpha = img.getchannel("A")
        img = ImageEnhance.Color(img.convert("RGB")).enhance(saturation).convert("RGBA")
        img.putalpha(alpha)
    # Crop to the artwork's bounding box so every icon fills its canvas the
    # way the classic 32px originals do — AI generations come with generous
    # empty margins that would otherwise render the icon visibly smaller.
    bbox = img.getchannel("A").getbbox()
    if bbox:
        img = img.crop(bbox)
    img = pad_to_square(img)
    margin = grid // 24
    inner = grid - 2 * margin
    scaled = img.resize((inner, inner), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (grid, grid), (0, 0, 0, 0))
    canvas.paste(scaled, (margin, margin))
    img = quantize(canvas, colors, dither)
    return img.resize((size, size), Image.Resampling.NEAREST)


def normalize(src: Image.Image, size: int) -> Image.Image:
    img = pad_to_square(src.convert("RGBA"))
    resample = (
        Image.Resampling.NEAREST
        if img.width % size == 0 or size % img.width == 0
        else Image.Resampling.LANCZOS
    )
    return img.resize((size, size), resample)


def iconback(size: int) -> Image.Image:
    """A small Win95 program window: title bar with its three buttons over a
    white client area. Launchers draw an unthemed app's own icon, scaled by
    appfilter's <scale> (0.6), centred on top; the client area is centred on
    the canvas so the icon sits inside it, under the title bar."""
    grid = 48
    p = size // grid  # one art pixel

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def box(x0, y0, x1, y1, color):  # inclusive art-pixel coordinates
        d.rectangle([x0 * p, y0 * p, (x1 + 1) * p - 1, (y1 + 1) * p - 1], fill=color)

    left, top, right, bottom = 3, 1, 44, 42
    box(left, top, right, bottom, BEVEL_DARK)
    box(left, top, right - 1, bottom - 1, BEVEL_FACE)
    box(left, top, right - 1, top, BEVEL_LIGHT)
    box(left, top, left, bottom - 1, BEVEL_LIGHT)
    box(left + 1, bottom - 1, right - 1, bottom - 1, BEVEL_SHADOW)
    box(right - 1, top + 1, right - 1, bottom - 1, BEVEL_SHADOW)

    # Title bar, navy fading to blue like an active Win95 window.
    tb_top, tb_bottom = top + 2, top + 5
    x0, x1 = left + 2, right - 2
    navy, blue = (0, 0, 128), (16, 132, 208)
    for x in range(x0, x1 + 1):
        t = (x - x0) / max(1, x1 - x0)
        box(x, tb_top, x, tb_bottom, tuple(round(a + (b - a) * t) for a, b in zip(navy, blue)) + (255,))
    # Minimize, maximize and close as tiny raised buttons; close carries an x.
    for i in range(3):
        bx = x1 - 10 + i * 3 + (1 if i == 2 else 0)
        box(bx, tb_top, bx + 2, tb_bottom - 1, BEVEL_FACE)
        box(bx, tb_bottom - 1, bx + 2, tb_bottom - 1, BEVEL_SHADOW)
        box(bx + 2, tb_top, bx + 2, tb_bottom - 1, BEVEL_SHADOW)
        glyph = [(bx, tb_bottom - 2), (bx + 1, tb_bottom - 2)] if i == 0 else \
                [(bx, tb_top), (bx + 1, tb_top)] if i == 1 else [(bx, tb_top), (bx + 1, tb_bottom - 2)]
        for gx, gy in glyph:
            box(gx, gy, gx, gy, BEVEL_DARK)

    # Sunken white client area, centred on the canvas.
    c_top, c_bottom = tb_bottom + 2, bottom - 2
    box(left + 2, c_top, right - 2, c_bottom, BEVEL_SHADOW)
    box(left + 3, c_top + 1, right - 2, c_bottom, (255, 255, 255, 255))
    return img


def collect_inputs(path: Path) -> list[Path]:
    if path.is_dir():
        return sorted(p for p in path.iterdir() if p.suffix.lower() in {".png", ".webp", ".jpg", ".jpeg"})
    return [path]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("mode", choices=["win95ify", "normalize", "iconback"])
    parser.add_argument("input", nargs="?", help="source image or directory (not used by iconback)")
    parser.add_argument("-o", "--out", required=True, help="output directory")
    parser.add_argument("--size", type=int, default=192, help="output canvas size (default 192)")
    parser.add_argument("--grid", type=int, default=48, help="pixel grid for win95ify (default 48)")
    parser.add_argument("--colors", type=int, default=16, choices=[16, 256], help="palette size for win95ify")
    parser.add_argument("--dither", action="store_true", help="Floyd-Steinberg dithering during quantization")
    parser.add_argument("--strip-bg", action="store_true", help="flood-fill the solid background to transparent (win95ify mode)")
    parser.add_argument("--saturation", type=float, default=1.0, help="saturation boost before quantization, e.g. 1.3 (win95ify mode)")
    args = parser.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.mode == "iconback":
        dest = out_dir / "iconback.png"
        iconback(args.size).save(dest)
        print(f"wrote {dest}")
        return 0

    if not args.input:
        parser.error(f"mode '{args.mode}' requires an input image or directory")

    for src_path in collect_inputs(Path(args.input)):
        src = Image.open(src_path)
        if args.mode == "win95ify":
            result = win95ify(src, args.size, args.grid, args.colors, args.dither, args.strip_bg, args.saturation)
        else:
            result = normalize(src, args.size)
        dest = out_dir / (src_path.stem + ".png")
        result.save(dest)
        print(f"{src_path.name}: {src.width}x{src.height} -> {dest} ({args.size}x{args.size})")

    return 0


if __name__ == "__main__":
    sys.exit(main())
