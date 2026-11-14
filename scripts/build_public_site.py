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


# Signups land in a Google Form owned by Burooj; he adds each address to the tester group by hand,
# because Google Groups does not let people join it themselves.
TEST_FORM = "https://docs.google.com/forms/d/e/1FAIpQLSfflI9hpFrBgZoSNiMLFrtoLupLkKYHInzG-wkfA1qwRUIJYA/formResponse"
TEST_FORM_FIELD = "entry.2041371387"
TEST_OPT_IN = "https://play.google.com/apps/testing/dev.burooj.speedbreaker"
TEST_FEEDBACK = "burooj.bj+playsupport@gmail.com"
# The tester page is a short flow, not a document: one column, two buttons, the app's palette.
TEST_STYLE = """
:root{--bg:#F7F2E8;--card:#FBF8F2;--ink:#1C1B17;--soft:#5F5A4F;--line:#DCD3C2;--moss:#4F6450;
--on-moss:#F7F2E8;--wash:#DFE5D6;color-scheme:light dark}
@media(prefers-color-scheme:dark){:root{--bg:#131311;--card:#1C1B18;--ink:#ECE5D6;--soft:#ABA595;
--line:#3A3832;--moss:#A9BDA6;--on-moss:#131311;--wash:#28302A}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);
font:17px/1.55 system-ui,sans-serif;-webkit-text-size-adjust:100%}
main{max-width:480px;margin:0 auto;padding:40px 20px 56px}
.mark{display:block;width:44px;height:44px;color:var(--ink);margin-bottom:24px}
h1{font:400 32px/1.2 Georgia,serif;margin:0 0 12px}.lead{color:var(--soft);margin:0 0 32px}
ol{list-style:none;margin:0;padding:0;display:grid;gap:14px}
li{background:var(--card);border:1px solid var(--line);border-radius:20px;padding:20px;
display:grid;grid-template-columns:32px 1fr;column-gap:14px}
.n{width:32px;height:32px;border-radius:50%;background:var(--wash);color:var(--moss);
display:grid;place-items:center;font-weight:600;font-size:15px}
li.done .n{background:var(--moss);color:var(--on-moss)}
h2{font:400 21px/1.3 Georgia,serif;margin:3px 0 4px}li p{margin:0;color:var(--soft)}
.go{grid-column:2;display:block;margin-top:16px;padding:14px 20px;border-radius:999px;
background:var(--moss);color:var(--on-moss);text-align:center;text-decoration:none;font-weight:600}
li.done .go{background:transparent;color:var(--moss);border:1px solid var(--line)}
form{grid-column:2;margin-top:16px;display:grid;gap:10px}
input{font:inherit;color:var(--ink);background:var(--bg);border:1px solid var(--line);border-radius:14px;
padding:13px 16px;width:100%}input:focus{outline:2px solid var(--moss);outline-offset:1px}
button.go{border:0;font:inherit;font-weight:600;cursor:pointer;width:100%;margin-top:0}
.sent{grid-column:2;margin:16px 0 0;color:var(--moss);display:none}li.done .sent{display:block}
li.done form{display:none}
.note{margin:28px 0 0;padding:16px 20px;border-radius:16px;background:var(--wash);font-size:15px}
footer{margin-top:28px;color:var(--soft);font-size:15px}a{color:var(--moss)}
"""


def tester_page():
    mark = ('<svg class="mark" viewBox="0 0 128 128" aria-hidden="true"><g fill="none" '
            'stroke="currentColor" stroke-width="8" stroke-linecap="round">'
            '<path d="M 49,24 C 10,37 10,91 49,104"/><path d="M 79,24 C 118,37 118,91 79,104"/>'
            '<circle cx="64" cy="64" r="6" fill="currentColor" stroke="none"/></g></svg>')
    description = "Help Speedbreaker reach Google Play: join the test in about three minutes."
    doc = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Test Speedbreaker</title><meta name="description" content="{escape(description, quote=True)}">
<link rel="canonical" href="{SITE}test.html"><meta name="robots" content="noindex">
<link rel="icon" href="favicon.svg" type="image/svg+xml"><link rel="icon" href="favicon.ico" sizes="48x48">
<link rel="apple-touch-icon" href="apple-touch-icon.png">
<meta name="theme-color" content="#F7F2E8" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="{THEME_COLOR}" media="(prefers-color-scheme: dark)">
<meta property="og:type" content="website"><meta property="og:site_name" content="Speedbreaker">
<meta property="og:title" content="Test Speedbreaker"><meta property="og:description" content="{escape(description, quote=True)}">
<meta property="og:url" content="{SITE}test.html"><meta property="og:image" content="{SITE}{SHARE_IMAGE}">
<meta name="twitter:card" content="summary_large_image"><meta name="twitter:image" content="{SITE}{SHARE_IMAGE}">
<style>{TEST_STYLE}</style></head><body><main>
<a href="./" aria-label="Speedbreaker">{mark}</a>
<h1>Help Speedbreaker reach Google&nbsp;Play</h1>
<p class="lead">Google asks new apps for 12 testers who stay in the test for 14 days in a row.
It takes about three minutes on an Android phone.</p>
<ol>
<li id="join"><span class="n">1</span><div><h2>Send your Gmail address</h2>
<p>The one on your Android phone. Burooj adds you to the test, usually within a day.</p></div>
<form action="{TEST_FORM}" method="post" target="_blank">
<input type="email" name="{TEST_FORM_FIELD}" required autocomplete="email" inputmode="email"
placeholder="you@gmail.com" aria-label="Gmail address">
<button class="go" type="submit">Send</button></form>
<p class="sent">Sent. Burooj will let you know when you’re added.</p></li>
<li id="opt-in"><span class="n">2</span><div><h2>Become a tester</h2>
<p>Once you’re added, tap <b>Become a tester</b>, then install Speedbreaker from Google Play.</p></div>
<a class="go" href="{TEST_OPT_IN}" target="_blank" rel="noopener">Become a tester</a></li>
<li><span class="n">3</span><div><h2>Set it up and keep it</h2>
<p>Open it, turn it on when it asks, and pick one app you open too often.
Then keep it installed, and stay in the test, for 14 days in a row.</p></div></li>
</ol>
<p class="note">Installed Speedbreaker from GitHub before? Uninstall that copy first;
Play can’t install over it.</p>
<footer>Thoughts or problems: <a href="mailto:{TEST_FEEDBACK}">{TEST_FEEDBACK}</a>.
Thank you for the fourteen days.</footer>
</main><script>
// A step you have done stays ticked when you come back to this page.
const tick = (step, key) => {{
  step.classList.add("done");
  try {{ localStorage.setItem(key, "1"); }} catch (e) {{}}
}};
for (const id of ["join", "opt-in"]) {{
  const step = document.getElementById(id), key = "sb-test-" + id;
  try {{ if (localStorage.getItem(key)) step.classList.add("done"); }} catch (e) {{}}
}}
// Send the address without leaving the page; Google's form gives no readable reply, so a
// finished request counts as sent. Without script, the form posts to Google in a new tab.
const join = document.getElementById("join"), form = join.querySelector("form");
form.addEventListener("submit", async (event) => {{
  event.preventDefault();
  const button = form.querySelector("button");
  button.disabled = true; button.textContent = "Sending…";
  try {{
    await fetch(form.action, {{ method: "POST", mode: "no-cors", body: new FormData(form) }});
    tick(join, "sb-test-join");
  }} catch (e) {{
    button.disabled = false; button.textContent = "Couldn’t send. Try again";
  }}
}});
const optIn = document.getElementById("opt-in");
optIn.querySelector(".go").addEventListener("click", () => tick(optIn, "sb-test-opt-in"));
</script></body></html>'''
    (OUT / "test.html").write_text(doc)


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
Phone permission is optional; without it a call ringing without a ringtone (silent, vibrate or Do Not Disturb) can be hidden for the length of a breath. Calling apps exposing neither Android call state nor communication audio
may not be detected. You choose every target and optional detour.</p>
<p>No account, ads or analytics, and no timeline of your app use. Settings, pause state and resettable running totals of your breaths, including the time breathed on each of the last seven days, stay on the phone.</p>''')
    page("privacy.html", "Privacy policy",
         "What Speedbreaker observes, what stays on your phone, and the permissions it asks for.",
         render_document("docs/privacy.md"))
    page("support.html", "Setup and support",
         "Set up Speedbreaker, turn it off, and report a problem.", f'''
<h1>Help with Speedbreaker</h1><p><a href="{REPO}/blob/main/docs/getting-started.md">Setup and troubleshooting</a></p>
<p>You can stop observation in Android Settings → Accessibility → Speedbreaker.
Clear app storage or uninstall to remove local settings and pause state.</p>
<h2>Report a problem</h2><p><a href="{REPO}/issues/new/choose">Open a public GitHub issue</a> with your phone model,
Android/app versions, steps and what happened. Remove personal details from screenshots and logs.</p>
<p>For private security or privacy concerns, use
<a href="{REPO}/security/advisories/new">GitHub private vulnerability reporting</a>.</p>
<p>Support does not require a payment. Every app feature is free.</p>''')
    tester_page()
    print(f"Rendered public pages: {OUT}")


if __name__ == "__main__":
    main()
