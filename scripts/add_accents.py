#!/usr/bin/env python3
"""Add accented letters to the MS Sans Serif pixel fonts, which ship without
them. Each letter is the base glyph plus a pixel-drawn accent on the font's
11-pixel grid; ¿ and ¡ are ? and ! turned upside down. Safe to re-run: letters
the font already has are skipped.

Usage:
  .venv/bin/python scripts/add_accents.py
"""

from pathlib import Path

from fontTools.pens.recordingPen import RecordingPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

FONTS = Path(__file__).resolve().parent.parent / "app/src/main/res/font"
PIXEL = 4096 / 11  # the fonts are drawn on an 11-row grid of 4096 units
X_HEIGHT_ROW = 6   # lowercase letters are 6 pixels tall
CAP_ROW = 9        # capitals are 9 pixels tall

# Accent pixels as (column offset from the letter's center, row). Lowercase
# accents sit in rows 7-8, capitals get one row above their height.
LOWER = {
    "acute": [(0, 7), (1, 8)],
    "grave": [(1, 7), (0, 8)],
    "dieresis": [(-1, 7), (2, 7)],
    "tilde": [(-1, 7), (0, 8), (1, 7), (2, 8)],
    "circumflex": [(-1, 7), (0, 8), (1, 7)],
}
UPPER = {
    "acute": [(0, CAP_ROW), (1, CAP_ROW)],
    "grave": [(0, CAP_ROW), (1, CAP_ROW)],
    "dieresis": [(-1, CAP_ROW), (2, CAP_ROW)],
    "tilde": [(-1, CAP_ROW), (0, CAP_ROW), (1, CAP_ROW), (2, CAP_ROW)],
    "circumflex": [(0, CAP_ROW), (1, CAP_ROW)],
}
CEDILLA = [(0, -1), (0, -2), (-1, -2)]

LETTERS = {
    "á": ("a", "acute"), "é": ("e", "acute"), "í": ("i", "acute"), "ó": ("o", "acute"), "ú": ("u", "acute"),
    "à": ("a", "grave"), "è": ("e", "grave"), "ò": ("o", "grave"),
    "ü": ("u", "dieresis"), "ï": ("i", "dieresis"), "ñ": ("n", "tilde"),
    "â": ("a", "circumflex"), "ê": ("e", "circumflex"), "ô": ("o", "circumflex"),
    "Á": ("A", "acute"), "É": ("E", "acute"), "Í": ("I", "acute"), "Ó": ("O", "acute"), "Ú": ("U", "acute"),
    "Ü": ("U", "dieresis"), "Ñ": ("N", "tilde"),
}


def rect(pen, col, row):
    x0, y0 = round(col * PIXEL), round(row * PIXEL)
    x1, y1 = round((col + 1) * PIXEL), round((row + 1) * PIXEL)
    pen.moveTo((x0, y0))
    pen.lineTo((x0, y1))
    pen.lineTo((x1, y1))
    pen.lineTo((x1, y0))
    pen.closePath()


def contours(glyph_set, name):
    rec = RecordingPen()
    glyph_set[name].draw(rec)
    out, current = [], []
    for op, args in rec.value:
        current.append((op, args))
        if op in ("closePath", "endPath"):
            out.append(current)
            current = []
    return out


def replay(pen, contour_list):
    for contour in contour_list:
        for op, args in contour:
            getattr(pen, op)(*args)


def add_glyph(font, char, glyph):
    name = f"uni{ord(char):04X}"
    font["glyf"].glyphs[name] = glyph
    order = font.getGlyphOrder()
    if name not in order:
        order.append(name)
        font.setGlyphOrder(order)
    for table in font["cmap"].tables:
        if table.isUnicode():
            table.cmap[ord(char)] = name
    return name


def patch(path):
    font = TTFont(path)
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    added = []
    for char, (base, accent) in LETTERS.items():
        if ord(char) in cmap or ord(base) not in cmap:
            continue
        base_name = cmap[ord(base)]
        parts = contours(glyph_set, base_name)
        if base == "i":  # drop the dot: keep only contours inside the x-height
            parts = [c for c in parts if max(p[1] for op, args in c for p in args if args) <= X_HEIGHT_ROW * PIXEL + 1]
        xs = [p[0] for c in parts for op, args in c for p in args if args]
        center = round(((min(xs) + max(xs)) / 2) / PIXEL - 0.5)
        pen = TTGlyphPen(glyph_set)
        replay(pen, parts)
        for dx, row in (UPPER if base.isupper() else LOWER)[accent]:
            rect(pen, center + dx, row)
        name = add_glyph(font, char, pen.glyph())
        font["hmtx"].metrics[name] = font["hmtx"].metrics[base_name]
        added.append(char)

    for char, base in (("ç", "c"), ("Ç", "C")):
        if ord(char) in cmap or ord(base) not in cmap:
            continue
        parts = contours(glyph_set, cmap[ord(base)])
        xs = [p[0] for c in parts for op, args in c for p in args if args]
        center = round(((min(xs) + max(xs)) / 2) / PIXEL - 0.5)
        pen = TTGlyphPen(glyph_set)
        replay(pen, parts)
        for dx, row in CEDILLA:
            rect(pen, center + dx, row)
        name = add_glyph(font, char, pen.glyph())
        font["hmtx"].metrics[name] = font["hmtx"].metrics[cmap[ord(base)]]
        added.append(char)

    # ¿ and ¡: the upright mark rotated 180 degrees, hanging two pixels below the baseline.
    for char, base in (("¿", "?"), ("¡", "!")):
        if ord(char) in cmap or ord(base) not in cmap:
            continue
        parts = contours(glyph_set, cmap[ord(base)])
        pts = [p for c in parts for op, args in c for p in args if args]
        xmax = max(p[0] for p in pts)
        ymax = max(p[1] for p in pts)
        pen = TTGlyphPen(glyph_set)
        replay(TransformPen(pen, (-1, 0, 0, -1, xmax, ymax - 2 * PIXEL)), parts)
        name = add_glyph(font, char, pen.glyph())
        font["hmtx"].metrics[name] = font["hmtx"].metrics[cmap[ord(base)]]
        added.append(char)

    font["maxp"].numGlyphs = len(font.getGlyphOrder())
    font.save(path)
    print(f"{path.name}: added {''.join(added) or 'nothing'}")


if __name__ == "__main__":
    for f in ("ms_sans_serif.ttf", "ms_sans_serif_bold.ttf"):
        patch(FONTS / f)
