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
STYLE = """
body{background:#F7F2E8;color:#1C1B17;max-width:760px;margin:48px auto;padding:0 24px;
font:18px/1.65 system-ui,sans-serif}h1,h2,h3{font-family:Georgia,serif;line-height:1.25}
h1{font-size:42px}a{color:#385444}nav{font-size:16px}table{border-collapse:collapse}
td,th{border:1px solid #DCD3C2;padding:10px;text-align:left}img{max-width:100%}
code{overflow-wrap:anywhere}footer{margin-top:48px;border-top:1px solid #DCD3C2;padding-top:16px}
@media(prefers-color-scheme:dark){body{background:#131311;color:#ECE5D6}a{color:#AFBBA2}}
"""


def page(name, title, content):
    doc = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="{escape(title)}"><title>{escape(title)} · Speedbreaker</title>
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
    shutil.copyfile(ROOT / "docs/brand/hero-light.png", OUT / "hero.png")
    page("index.html", "A breathing pause before the apps you choose", f'''
<h1>Make the next tap intentional.</h1><p>Speedbreaker adds a breathing pause before the Android apps you choose.
Then continue, leave, or open somewhere else. Optional check-ins bring the pause back during continuous use.</p>
<img src="hero.png" alt="Speedbreaker — a moment to breathe. Room to choose.">
<p><a href="{REPO}/releases/tag/v1.0.1">Download stable Android 1.0.1</a> ·
<a href="{REPO}/blob/main/docs/getting-started.md">Setup guide</a></p>
<p>Android 8.0 or newer. GitHub APK is available. F-Droid and Google Play distribution are in preparation;
no store availability is claimed.</p><h2>What the pause does</h2>
<p>The first eight seconds hold ordinary navigation. Settings and Android-reported calls yield.
Breaths stay off without Phone permission. Calling apps exposing neither Android call state nor communication audio
may not be detected. You choose every target and optional redirect.</p>
<p>No account, ads, analytics or usage history. Settings and pause state stay on the phone.</p>''')
    page("privacy.html", "Privacy policy", render_document("docs/privacy.md"))
    page("support.html", "Setup and support", f'''
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
