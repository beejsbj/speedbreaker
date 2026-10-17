#!/usr/bin/env -S uv run --script
# SPDX-License-Identifier: Apache-2.0
# /// script
# requires-python = ">=3.11"
# dependencies = ["Markdown==3.11"]
# ///
"""Render the public privacy/support pages from repository documents."""
from html import escape
from pathlib import Path
import re
import shutil
import markdown

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/public-site"
REPO = "https://github.com/beejsbj/speedbreaker"
SITE = "https://beejsbj.github.io/speedbreaker/"
SHARE_IMAGE = "social-preview.png"
SHARE_ALT = "Speedbreaker: a moment to breathe. Room to choose."
THEME_COLOR = "#131311"  # the favicon tile and social image ground
STYLE = """
body{background:#F7F2E8;color:#1C1B17;max-width:760px;margin:48px auto;padding:0 24px;
font:18px/1.65 system-ui,sans-serif}h1,h2,h3{font-family:Georgia,serif;line-height:1.25}
h1{font-size:42px}a{color:#385444}nav{font-size:16px}table{border-collapse:collapse}
td,th{border:1px solid #DCD3C2;padding:10px;text-align:left}img{max-width:100%}
code{overflow-wrap:anywhere}footer{margin-top:48px;border-top:1px solid #DCD3C2;padding-top:16px}
@media(prefers-color-scheme:dark){body{background:#131311;color:#ECE5D6}a{color:#AFBBA2}}
"""


def page(name, title, description, content):
    url = SITE if name == "index.html" else SITE + name
    full_title = f"{title} · Speedbreaker"
    doc = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{escape(full_title)}</title><meta name="description" content="{escape(description, quote=True)}">
<link rel="canonical" href="{url}">
<link rel="icon" href="favicon.svg" type="image/svg+xml"><link rel="icon" href="favicon.ico" sizes="48x48">
<link rel="apple-touch-icon" href="apple-touch-icon.png"><meta name="theme-color" content="{THEME_COLOR}">
<meta property="og:type" content="website"><meta property="og:site_name" content="Speedbreaker">
<meta property="og:title" content="{escape(full_title, quote=True)}">
<meta property="og:description" content="{escape(description, quote=True)}"><meta property="og:url" content="{url}">
<meta property="og:image" content="{SITE}{SHARE_IMAGE}"><meta property="og:image:width" content="1280">
<meta property="og:image:height" content="640"><meta property="og:image:alt" content="{escape(SHARE_ALT)}">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="{escape(full_title, quote=True)}">
<meta name="twitter:description" content="{escape(description, quote=True)}">
<meta name="twitter:image" content="{SITE}{SHARE_IMAGE}"><meta name="twitter:image:alt" content="{escape(SHARE_ALT)}">
<style>{STYLE}</style></head><body><nav><a href="./">Speedbreaker</a> · <a href="privacy.html">Privacy</a> ·
<a href="support.html">Help</a> · <a href="{REPO}">Source</a></nav><main>{content}</main>
<footer>All app features are free. No medical claims. <a href="{REPO}/blob/main/LICENSE">Apache-2.0</a>.</footer>
</body></html>'''
    (OUT / name).write_text(doc)


def render_document(path):
    source = (ROOT / path).read_text()
    # Repository-relative implementation links resolve to the public source,
    # not nonexistent files on the static policy host.
    def absolute(match):
        label, target = match.groups()
        if target.startswith(("https://", "http://", "#")):
            return match.group(0)
        relative = (Path(path).parent / target).as_posix()
        parts = []
        for part in relative.split("/"):
            if part == "..":
                parts.pop()
            elif part != ".":
                parts.append(part)
        return f"[{label}]({REPO}/blob/main/{'/'.join(parts)})"
    source = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", absolute, source)
    return markdown.markdown(source, extensions=["tables"])


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / ".nojekyll").touch()
    brand = ROOT / "docs/brand"
    for source, target in (
        ("hero-light.png", "hero.png"),
        ("hero.png", "hero-dark.png"),
        (SHARE_IMAGE, SHARE_IMAGE),
        ("favicon.svg", "favicon.svg"),
        ("favicon.ico", "favicon.ico"),
        # iOS composes transparent corners on black, which matches the dark tile.
        ("icon-dark-256.png", "apple-touch-icon.png"),
    ):
        shutil.copyfile(brand / source, OUT / target)
    page("index.html", "A breathing pause before the apps you choose",
         "Speedbreaker adds a calm breathing pause before the Android apps you choose. "
         "Then continue, leave, or open somewhere else. No account, ads or analytics.", f'''
<h1>Make the next tap intentional.</h1><p>Speedbreaker adds a breathing pause before the Android apps you choose.
Then continue, leave, or open somewhere else. Optional check-ins bring the pause back during continuous use.</p>
<picture><source media="(prefers-color-scheme: dark)" srcset="hero-dark.png">
<img src="hero.png" alt="Speedbreaker — a moment to breathe. Room to choose."></picture>
<p><a href="{REPO}/releases/latest">Download the latest Android APK</a> ·
<a href="{REPO}/blob/main/docs/getting-started.md">Setup guide</a></p>
<p>Android 8.0 or newer. GitHub APK is available. F-Droid and Google Play distribution are in preparation;
no store availability is claimed.</p><h2>What the pause does</h2>
<p>The first eight seconds hold ordinary navigation. Settings and Android-reported calls yield.
Breaths stay off without Phone permission. Calling apps exposing neither Android call state nor communication audio
may not be detected. You choose every target and optional redirect.</p>
<p>No account, ads, analytics or per-app usage history. Settings, pause state and a resettable total of time breathed stay on the phone.</p>''')
    page("privacy.html", "Privacy policy",
         "What Speedbreaker observes, what stays on your phone, and the permissions it asks for.",
         render_document("docs/privacy.md"))
    page("support.html", "Setup and support",
         "Set up Speedbreaker, turn it off, and report a problem.", f'''
<h1>Help with Speedbreaker</h1><p><a href="{REPO}/blob/main/docs/getting-started.md">Setup and troubleshooting</a></p>
<p>You can stop observation in Android Settings → Accessibility → Speedbreaker attention pauses.
Clear app storage or uninstall to remove local settings and pause state.</p>
<h2>Report a problem</h2><p><a href="{REPO}/issues/new/choose">Open a public GitHub issue</a> with your phone model,
Android/app versions, steps and what happened. Remove personal details from screenshots and logs.</p>
<p>For private security or privacy concerns, use
<a href="{REPO}/security/advisories/new">GitHub private vulnerability reporting</a>.</p>
<p>Support does not require a payment. Every app feature is free.</p>''')
    print(f"Rendered public pages: {OUT}")


if __name__ == "__main__":
    main()
