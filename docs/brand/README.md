# Speedbreaker identity kit

![Speedbreaker: a moment to breathe, room to choose.](hero.png)

**Open interval.** Two rounded arcs leave room around a still center. The mark
borrows the breathing orb's center and circular rhythm, opening its boundary
into a pair of gentle brackets. The two sides have equal weight, like Continue
and Leave. Generous space, a reflective serif, and warm neutrals carry the
identity without racing, punishment, or productivity-score imagery.

This is a publication identity chosen under the October 11 delegated brief.
It follows the [calm UI direction](../design/calm-ui.md) and the
[interaction contract](../interaction-contract.md). The installed app's lavender
triangle launcher remains unchanged; adopting this mark inside Android would
be a separate change. No screenshots or product behavior were modified.

## Assets and use

| Asset | Use |
| --- | --- |
| [hero.png](hero.png) / [hero.svg](hero.svg) | Default dark README hero, 1600 × 720. |
| [hero-light.png](hero-light.png) / [hero-light.svg](hero-light.svg) | Ivory README alternative, same dimensions. |
| [social-preview.png](social-preview.png) | Default dark GitHub social image, 1280 × 640, 92,344 bytes. |
| [social-preview-light.png](social-preview-light.png) | Ivory social image, 1280 × 640, 91,027 bytes. |
| [logo-dark.svg](logo-dark.svg) / [logo-light.svg](logo-light.svg) | Transparent mark + wordmark; suffix names the intended background. |
| [mark-dark.svg](mark-dark.svg) / [mark-light.svg](mark-light.svg) | Transparent standalone mark, 128-unit viewBox. |
| [icon-dark.svg](icon-dark.svg) / [icon-light.svg](icon-light.svg) | Rounded tile with transparent corners. |
| `icon-{dark,light}-{16,32,48,64,128,256,512}.png` | Explicit raster sizes; 512 px is useful for repository/avatar artwork. |
| [favicon.svg](favicon.svg) / [favicon.ico](favicon.ico) | Fixed dark tile; ICO contains individually rendered 16, 32, and 48 px frames. |
| [review/contact-sheet.png](review/contact-sheet.png) | Both schemes, social thumbnails, native-size icons, and logo lockups. |

GitHub recommends **1280 × 640**, a PNG/JPG/GIF under **1 MB**; both social
exports satisfy these constraints and have opaque backgrounds.
[GitHub's social-preview guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/customizing-your-repositorys-social-media-preview)
was checked on October 11, 2026. Uploading the image in repository settings is
the publication owner's step; committing it alone does not set the preview.

README insertion from the repository root:

```markdown
![Speedbreaker — a moment to breathe. Room to choose.](docs/brand/hero.png)
```

- Preserve the artwork's proportions and open gaps. Keep at least 16 units of
  clear space around the mark's 128-unit canvas when placing it beside content.
  The icon tiles already supply their own inset.
- Use standalone marks from 24 px, icon tiles from 16 px, and complete logo
  lockups from 240 px wide. Below that, use the icon and ordinary accessible text.
- Use the provided schemes on quiet, solid backgrounds. A transparent
  `*-dark.svg` is ivory artwork intended for dark surfaces.
- Keep the wordmark capitalization. Avoid outlines, shadows, gradients, extra
  rings, or rotating the arcs. The still center is deliberate.
- Supply equivalent alt text when embedding PNG/SVG as an image. The SVGs carry
  titles; their lettering is outlined and has no external font dependency.

## Palette and type

All palette values come directly from the app's
[`Palette` in Theme.kt](../../app/src/main/java/dev/burooj/speedbreaker/presentation/theme/Theme.kt).
[source/palette.json](source/palette.json) owns their publication mapping.

| Role | Dark | Light |
| --- | --- | --- |
| Ground | NightInk `#131311` | Ivory `#F7F2E8` |
| Mark / primary text | NightIvory `#ECE5D6` | Ink `#1C1B17` |
| Secondary text | NightIvorySoft `#ABA595` | InkSoft `#5F5A4F` |
| Decorative rule | NightHairline `#3A3832` | Hairline `#DCD3C2` |

The warm neutrals tie the kit to the app rather than adding another accent.
Moss and brick retain their product meanings of active and repair states.
Primary text contrast is 14.83:1 dark / 15.45:1 light; secondary text is
7.57:1 dark / 6.15:1 light. The thin rule is decorative.

Liberation Serif Regular gives the name the app's reflective serif character;
Lato Regular gives supporting language open, readable forms. Both are bundled
unmodified under the SIL Open Font License, with notices in
[fonts/Liberation-LICENSE.txt](fonts/Liberation-LICENSE.txt) and
[fonts/Lato-LICENSE.txt](fonts/Lato-LICENSE.txt). The symbol and compositions were
drawn for this kit. There is no stock imagery, external logo, or generated raster.

The original mark, compositions, documentation, and export code use the project's
**Apache-2.0 license**. The bundled font software remains under its own SIL Open
Font License and retains its copyright and license notices. The font licenses
permit these rendered documents and do not relicense the kit's original artwork.

## Reproduce or edit

The hand-authored [source/mark.svg](source/mark.svg) owns the symbol geometry.
[export.py](export.py) owns type layout, compositions, tile shape, and exports.
Edit those sources, then regenerate; the root SVGs and PNGs are derived files.
The fonts live in `fonts/`, so exports do not depend on installed system fonts.

```sh
uv run --locked docs/brand/export.py
```

Requires Python 3.11+, `uv`, and the native Cairo library (for example,
`libcairo2` on Debian). `export.py.lock` locks Python dependencies; the first
run may download them. Exporting took about eight seconds on the working host
and used no browser, Gradle, or GPU. Different native Cairo versions may change
raster edge pixels; vector sources remain portable.

## Rendered review · October 11, 2026

The final dark hero was inspected at 1600 × 720; both heroes were inspected at
680 × 306 in the contact sheet. Both social compositions were inspected at
320 × 160, with the name, mark, and two-line promise still readable. The social
tagline was enlarged after the first thumbnail review. The small Android footer
is intentionally secondary and should not carry essential information alone.

Both icon schemes were inspected at native 16, 32, 48, 64, and 128 px. The arcs
remain separated and the center stays visible at 16 px. Both transparent logo
lockups were inspected on their intended backgrounds without clipping.

The exporter checks SVG parseability and absence of live text/raster imports,
social dimensions/opacity/file size, hero dimensions, all PNG icon sizes, and
ICO frame sizes and pixel equality with their corresponding PNGs. A second
locked export reproduced every SVG, PNG, and ICO byte-for-byte on this host.

The bounded kit is complete and ready for the publication owner. README wiring,
GitHub upload, and any later launcher adoption remain outside this branch.
