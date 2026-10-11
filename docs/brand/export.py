#!/usr/bin/env -S uv run --script
# SPDX-License-Identifier: Apache-2.0
# /// script
# requires-python = ">=3.11"
# dependencies = ["cairosvg==2.8.2", "pillow==11.3.0", "fonttools==4.60.1"]
# ///
"""Small, CPU-only vector exports. Run: uv run docs/brand/export.py.

No browser, Android build, network asset, system font, or AI raster input.
The first uv invocation may download the pinned Python dependencies.
"""

from html import escape
from io import BytesIO
import json
from pathlib import Path
import xml.etree.ElementTree as ET

import cairosvg
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
PALETTE = json.loads((ROOT / "source/palette.json").read_text())
SVG_NS = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG_NS)
MARK = ET.parse(ROOT / "source/mark.svg").find(f"{{{SVG_NS}}}g")
ART = ET.tostring(MARK, encoding="unicode")
FONTS = {
    "serif": TTFont(ROOT / "fonts/LiberationSerif-Regular.ttf"),
    "sans": TTFont(ROOT / "fonts/Lato-Regular.ttf"),
}


def text(value, x, y, size, color, face="sans", tracking=0):
    """Outline from bundled fonts, with legacy kerning; no runtime font reliance."""
    font = FONTS[face]
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    units = font["head"].unitsPerEm
    kern = {}
    if "kern" in font:
        for table in font["kern"].kernTables:
            if table.version == 0:
                kern.update(table.kernTable)
    cursor, previous, paths = 0, None, []
    for char in value:
        name = cmap[ord(char)]
        cursor += kern.get((previous, name), 0)
        pen = SVGPathPen(glyphs)
        glyphs[name].draw(pen)
        path = pen.getCommands()
        if path:
            paths.append(f'<path transform="translate({cursor:g} 0)" d="{path}"/>')
        cursor += glyphs[name].width + tracking * units / size
        previous = name
    return (f'<g aria-label="{escape(value, quote=True)}" fill="{color}" '
            f'transform="translate({x} {y}) scale({size / units:g} {-size / units:g})">'
            + "".join(paths) + "</g>")


def mark(x, y, size, color):
    return (f'<g color="{color}" transform="translate({x} {y}) scale({size / 128:g})">'
            + ART + "</g>")


def svg(width, height, title, body, background=None, desc=""):
    back = f'<rect width="{width}" height="{height}" fill="{background}"/>' if background else ""
    return (f'<svg xmlns="{SVG_NS}" width="{width}" height="{height}" '
            f'viewBox="0 0 {width} {height}" role="img" aria-labelledby="title desc">'
            f'<title id="title">{escape(title)}</title><desc id="desc">{escape(desc)}</desc>'
            + back + body + "</svg>\n")


def save(name, value, png=False):
    (ROOT / f"{name}.svg").write_text(value)
    if png:
        # Two-times rasterization then Lanczos reduces curve edge stair-stepping.
        tree = ET.fromstring(value)
        size = (int(tree.attrib["width"]), int(tree.attrib["height"]))
        raw = cairosvg.svg2png(bytestring=value.encode(), scale=2)
        image = Image.open(BytesIO(raw)).convert("RGB").resize(size, Image.Resampling.LANCZOS)
        image.save(ROOT / f"{name}.png", optimize=True)


def icon(theme):
    p = PALETTE[theme]
    body = f'<rect width="128" height="128" rx="28" fill="{p["background"]}"/>'
    # The mark's own inset is its clear space; no second inset at small sizes.
    return svg(128, 128, "Speedbreaker", body + mark(0, 0, 128, p["foreground"]),
               desc="Two open arcs and a still center. Publication icon.")


def composition(theme, hero=False):
    p = PALETTE[theme]
    w, h = (1600, 720) if hero else (1280, 640)
    # A spacious horizontal lockup; the large right-hand mark holds the silence.
    x = 100 if hero else 80
    baseline = 324 if hero else 296
    body = text("Speedbreaker", x, baseline, 116 if hero else 108, p["foreground"], "serif", -1)
    body += text("A moment to breathe.", x + 3, baseline + 80, 38 if hero else 40, p["foreground"])
    body += text("Room to choose.", x + 3, baseline + 133, 38 if hero else 40, p["foreground"])
    body += mark(1125 if hero else 905, 197 if hero else 183,
                 340 if hero else 288, p["foreground"])
    body += f'<path d="M {x} {h-112} H {w-x}" stroke="{p["line"]}"/>'
    body += text("A breathing pause for Android", x + 3, h - 62, 24 if hero else 28, p["secondary"])
    return svg(w, h, "Speedbreaker — a moment to breathe. Room to choose.", body,
               p["background"], "A breathing pause for Android. Two open arcs frame a still center.")


def export():
    for theme in PALETTE:
        p = PALETTE[theme]
        save(f"mark-{theme}", svg(128, 128, "Speedbreaker", mark(0, 0, 128, p["foreground"])))
        lockup = mark(0, 0, 128, p["foreground"])
        lockup += text("Speedbreaker", 151, 88, 78, p["foreground"], "serif", -0.65)
        save(f"logo-{theme}", svg(600, 128, "Speedbreaker", lockup))
        save(f"icon-{theme}", icon(theme))
        for size in (16, 32, 48, 64, 128, 256, 512):
            raw = cairosvg.svg2png(bytestring=icon(theme).encode(), output_width=size * 4, output_height=size * 4)
            im = Image.open(BytesIO(raw)).resize((size, size), Image.Resampling.LANCZOS)
            im.save(ROOT / f"icon-{theme}-{size}.png", optimize=True)
        save("hero" if theme == "dark" else "hero-light", composition(theme, hero=True), png=True)
        save("social-preview" if theme == "dark" else "social-preview-light", composition(theme), png=True)
    # Fixed dark tile makes the favicon visible on both light and dark browser chrome.
    (ROOT / "favicon.svg").write_text(icon("dark"))
    images = [Image.open(ROOT / f"icon-dark-{s}.png") for s in (16, 32, 48)]
    images[-1].save(ROOT / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)],
                    append_images=images[:-1], bitmap_format="png")
    review()
    validate()


def review():
    """Contact sheet uses the final exports, including native-pixel thumbnails."""
    canvas = Image.new("RGB", (1440, 1140), "#D6D3CC")
    draw = ImageDraw.Draw(canvas)
    label = ImageFont.truetype(str(ROOT / "fonts/Lato-Regular.ttf"), 20)
    def caption(x, y, value):
        draw.text((x, y), value, fill="#1C1B17", font=label)
    for x, theme in ((24, "dark"), (744, "light")):
        caption(x, 18, f"{theme.title()} / hero at 680 × 306")
        name = "hero" if theme == "dark" else "hero-light"
        canvas.paste(Image.open(ROOT / f"{name}.png").resize((680, 306), Image.Resampling.LANCZOS), (x, 50))
        caption(x, 380, "Social preview / 320 × 160 thumbnail")
        name = "social-preview" if theme == "dark" else "social-preview-light"
        canvas.paste(Image.open(ROOT / f"{name}.png").resize((320, 160), Image.Resampling.LANCZOS), (x, 418))
        caption(x, 606, "Icons / 16, 32, 48, 64, 128 px (native size)")
        cursor = x
        for size in (16, 32, 48, 64, 128):
            im = Image.open(ROOT / f"icon-{theme}-{size}.png")
            canvas.paste(im, (cursor, 650 + (128 - size) // 2), im)
            cursor += size + 28
        caption(x, 808, "Logo / 480 × 102, on its intended background")
        value = (ROOT / f"logo-{theme}.svg").read_bytes()
        raw = cairosvg.svg2png(bytestring=value, output_width=480, output_height=102)
        logo = Image.open(BytesIO(raw))
        draw.rectangle((x, 850, x + 680, 982), fill=PALETTE[theme]["background"])
        canvas.paste(logo, (x + 20, 865), logo)
    caption(24, 1020, "OPEN INTERVAL · Two open arcs. A still center. Ink and ivory from the app.")
    caption(24, 1060, "Publication assets only. Installed Android launcher and product behavior are unchanged.")
    canvas.save(ROOT / "review/contact-sheet.png", optimize=True)


def validate():
    for file in ROOT.glob("*.svg"):
        tree = ET.parse(file)
        assert not tree.findall(f".//{{{SVG_NS}}}text"), f"Live font dependency: {file}"
        assert not tree.findall(f".//{{{SVG_NS}}}image"), f"Raster dependency: {file}"
    for name in ("social-preview", "social-preview-light"):
        path = ROOT / f"{name}.png"
        with Image.open(path) as im:
            assert im.size == (1280, 640) and im.mode == "RGB"
        assert path.stat().st_size < 1_000_000
    for name in ("hero", "hero-light"):
        with Image.open(ROOT / f"{name}.png") as im:
            assert im.size == (1600, 720)
    with Image.open(ROOT / "favicon.ico") as im:
        assert im.ico.sizes() == {(16, 16), (32, 32), (48, 48)}
        for size in (16, 32, 48):
            frame = im.ico.getimage((size, size)).convert("RGBA")
            with Image.open(ROOT / f"icon-dark-{size}.png") as source:
                assert frame.tobytes() == source.convert("RGBA").tobytes()
    for theme in PALETTE:
        for size in (16, 32, 48, 64, 128, 256, 512):
            with Image.open(ROOT / f"icon-{theme}-{size}.png") as im:
                assert im.size == (size, size) and im.mode == "RGBA"
    print("Validated: standalone SVGs; opaque 1280×640 social PNGs under 1 MB; "
          "1600×720 heroes; 16/32/48 ICO frames.")


if __name__ == "__main__":
    export()
