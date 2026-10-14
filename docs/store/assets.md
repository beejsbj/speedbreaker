# Google Play store artwork

The source exports are in [`distribution/store-assets`](../../distribution/store-assets/):

- `icon.png` is the 512×512 Play icon. It uses the installed Android adaptive launcher foreground (pale triangle) and background (dark tile), not the separate publication arc in `docs/brand`.
- `featureGraphic.png` is original 1024×500 promotional artwork. It uses the brand palette and typefaces, with a pause motif and no simulated app screen.
- The matching SVGs contain outlined glyphs and need no installed fonts to render. The feature artwork was outlined from bundled Liberation Serif and Lato fonts. Their license notices remain in [`docs/brand/fonts`](../brand/fonts/); the Android vector and repository artwork are Apache-2.0.

Regenerate with `uv run --locked scripts/export_store_assets.py`; check committed bytes with `uv run --locked scripts/export_store_assets.py --check`; validate file properties and any integrated `en-US` listing with `uv run --locked scripts/check_store_metadata.py`. The first run may download the pinned Python dependencies. CI does these checks and uploads a freshly generated artwork artifact on matching PRs and main pushes. It does not submit a listing.

Google's [preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151) specify a 512×512 32-bit PNG icon, at most 1024 KB, and a 1024×500 JPEG or 24-bit PNG feature graphic. The exported icon has an alpha channel with fully opaque pixels; the feature PNG is RGB. Google's [listing limits](https://support.google.com/googleplay/android-developer/answer/9859152) are 30 characters for the name, 80 for the short description, and 4,000 for the full description. [Release notes](https://support.google.com/googleplay/android-developer/answer/9859348) allow up to 500 Unicode characters per language. The gate checks those limits when the parent branch's listing files are integrated.

## Still needed for publication

The artwork and local gate do not make the Play listing publishable. Capture at least two **actual app screenshots** from the current build on a real or test Android device, review them for accurate current UI and personal data, and add them through Play Console (or the later distribution workflow). Store their source and capture details in a future distribution asset change. Google's [screenshot requirements](https://support.google.com/googleplay/android-developer/answer/9866151) include 320–3840 px dimensions and a maximum 2:1 side ratio. Do not substitute mockups or generated screens.

Phone screenshots must be RGB JPEG or 24-bit PNG without an alpha channel.
The checker validates any committed files in
`fastlane/metadata/android/en-US/images/phoneScreenshots/`. Before uploading a
separate capture packet, run
`uv run --locked scripts/check_store_metadata.py --screenshots-dir CAPTURE_DIR --require-screenshots`.
An absent packet is reported explicitly during ordinary CI; it is not evidence
that the screenshot requirement is complete. Native 1080×2400 RGBA emulator
captures exceed the side ratio and have alpha, so they belong in test evidence.
Prepare separate actual 1080×1920 JPEG captures using the disposable emulator's
display setting and native screenshot command. Google recommends four 9:16
portrait screenshots at 1080 px or larger for promotional eligibility.

A preview video is optional. If one is produced later, record actual app footage, host it on YouTube, and add its URL to the listing only after it is live and reviewed. There is no placeholder video URL here. Console setup, screenshots, policy declarations, release upload, and Play review remain separate publishing steps.
