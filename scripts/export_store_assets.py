#!/usr/bin/env -S uv run --script
# SPDX-License-Identifier: Apache-2.0
# /// script
# requires-python = ">=3.11"
# dependencies = ["cairosvg==2.8.2", "pillow==11.3.0", "fonttools==4.60.1"]
# ///
"""Export Play artwork from repository vectors and bundled fonts."""

import argparse
from html import escape
from io import BytesIO
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import cairosvg
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "distribution/store-assets"
BRAND = ROOT / "docs/brand"
ANDROID = "http://schemas.android.com/apk/res/android"


def launcher_svg():
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    app = manifest.find("application")
    if app is None or app.get(f"{{{ANDROID}}}icon") != "@mipmap/ic_launcher":
        raise ValueError("Launcher icon reference changed; review export")
    adaptive = ET.parse(ROOT / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").getroot()
    background = adaptive.find("background")
    foreground = adaptive.find("foreground")
    if (background is None or foreground is None or
            background.get(f"{{{ANDROID}}}drawable") != "@color/ic_launcher_background" or
            foreground.get(f"{{{ANDROID}}}drawable") != "@drawable/ic_launcher_foreground"):
        raise ValueError("Adaptive icon layers changed; review export")
    colors = ET.parse(ROOT / "app/src/main/res/values/colors.xml").getroot()
    color = next((node.text for node in colors.findall("color")
                  if node.get("name") == "ic_launcher_background"), None)
    vector = ET.parse(ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml").getroot()
    width = vector.get(f"{{{ANDROID}}}viewportWidth")
    height = vector.get(f"{{{ANDROID}}}viewportHeight")
    if not color or not re.fullmatch(r"#[0-9A-Fa-f]{6}", color) or not width or not height:
        raise ValueError("Unsupported launcher vector")

    def path_shape(path):
        data = path.get(f"{{{ANDROID}}}pathData")
        fill = path.get(f"{{{ANDROID}}}fillColor")
        stroke = path.get(f"{{{ANDROID}}}strokeColor")
        if not data or not (fill or stroke):
            raise ValueError("Unsupported launcher path")
        attrs = []
        for value in (fill, stroke):
            if value and not re.fullmatch(r"#[0-9A-Fa-f]{6}", value):
                raise ValueError("Unsupported launcher path color")
        attrs.append(f'fill="{fill}"' if fill else 'fill="none"')
        if stroke:
            stroke_width = path.get(f"{{{ANDROID}}}strokeWidth", "1")
            cap = path.get(f"{{{ANDROID}}}strokeLineCap", "butt")
            if not re.fullmatch(r"[0-9.]+", stroke_width) or cap not in ("butt", "round", "square"):
                raise ValueError("Unsupported launcher stroke")
            attrs.append(f'stroke="{stroke}" stroke-width="{stroke_width}" stroke-linecap="{cap}"')
        return f'<path {" ".join(attrs)} d="{escape(data, quote=True)}"/>'

    shapes = []
    for node in vector:
        if node.tag == "path":
            shapes.append(path_shape(node))
        elif node.tag == "group":
            numbers = {}
            for key, default in (("translateX", "0"), ("translateY", "0"), ("scaleX", "1"), ("scaleY", "1")):
                value = node.get(f"{{{ANDROID}}}{key}", default)
                if not re.fullmatch(r"-?[0-9.]+", value):
                    raise ValueError("Unsupported launcher group transform")
                numbers[key] = value
            inner = [path_shape(child) for child in node if child.tag == "path"]
            if len(inner) != len(list(node)):
                raise ValueError("Unsupported launcher group content")
            shapes.append(
                f'<g transform="translate({numbers["translateX"]} {numbers["translateY"]}) '
                f'scale({numbers["scaleX"]} {numbers["scaleY"]})">' + "".join(inner) + "</g>"
            )
        else:
            raise ValueError("Unsupported launcher vector element")
    if not shapes:
        raise ValueError("Unsupported launcher vector")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" '
            f'viewBox="0 0 {width} {height}"><title>Speedbreaker launcher icon</title>'
            f'<rect width="{width}" height="{height}" fill="{color}"/>'
            + "".join(shapes) + "</svg>\n")


def outlined(value, x, y, size, color, font):
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
        if pen.getCommands():
            paths.append(f'<path transform="translate({cursor:g} 0)" d="{pen.getCommands()}"/>')
        cursor += glyphs[name].width
        previous = name
    return (f'<g aria-label="{escape(value, quote=True)}" fill="{color}" '
            f'transform="translate({x} {y}) scale({size / units:g} {-size / units:g})">'
            + "".join(paths) + "</g>")


def feature_svg():
    palette = json.loads((BRAND / "source/palette.json").read_text())["dark"]
    serif = TTFont(BRAND / "fonts/LiberationSerif-Regular.ttf")
    sans = TTFont(BRAND / "fonts/Lato-Regular.ttf")
    ink, secondary, line, back = (palette[k] for k in ("foreground", "secondary", "line", "background"))
    # A rhythm of interrupted lines describes the pause; no device UI or launcher mark.
    lines = "".join(f'<path d="M {x} {y} h {length}" stroke="{ink if i == 2 else line}" '
                    f'stroke-width="{7 if i == 2 else 2}" stroke-linecap="round"/>'
                    for i, (x, y, length) in enumerate(((706, 157, 190), (706, 214, 142),
                                                           (706, 271, 72), (790, 271, 106),
                                                           (706, 328, 142), (706, 385, 190))))
    body = outlined("Speedbreaker", 116, 135, 47, secondary, sans)
    body += outlined("A moment", 110, 266, 92, ink, serif)
    body += outlined("to breathe.", 110, 364, 92, ink, serif)
    body += lines
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" '
            f'viewBox="0 0 1024 500" role="img" aria-label="Speedbreaker — a moment to breathe">'
            f'<rect width="1024" height="500" fill="{back}"/>' + body + "</svg>\n")


def png(svg, size, mode):
    raw = cairosvg.svg2png(bytestring=svg.encode(), output_width=size[0] * 2,
                           output_height=size[1] * 2)
    image = Image.open(BytesIO(raw)).convert(mode).resize(size, Image.Resampling.LANCZOS)
    if mode == "RGBA" and image.getchannel("A").getextrema() != (255, 255):
        raise ValueError("Launcher icon is not opaque")
    buffer = BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def outputs():
    icon = launcher_svg()
    feature = feature_svg()
    return {
        "icon.svg": icon.encode(),
        "icon.png": png(icon, (512, 512), "RGBA"),
        "featureGraphic.svg": feature.encode(),
        "featureGraphic.png": png(feature, (1024, 500), "RGB"),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="compare committed outputs with a fresh export")
    args = parser.parse_args()
    for name, content in outputs().items():
        path = OUT / name
        if args.check:
            if not path.is_file() or path.read_bytes() != content:
                raise SystemExit(f"Stale store asset: {path}")
        else:
            OUT.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        print(f"{'Checked' if args.check else 'Exported'} {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
