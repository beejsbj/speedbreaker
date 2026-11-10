# First-run walk

`walk.py` installs Speedbreaker with no data on a **disposable emulator** and goes
through it the way a new person would: first open, declining and reopening, consent,
turning the service on in Android's own Accessibility screens, the Phone permission
(denied once, then allowed), choosing one app, and a first breath with Leave.

It saves a screenshot of every step, the visible text of each screen it could read
(`walk.json`), and Speedbreaker's own log lines (`speedbreaker-log.txt`). It fails when
a step a new person depends on does not happen: the disclosure not returning after a
decline, the disclosure returning after consent, the service not connecting, or no
breath on opening the chosen app.

The `First-run walk` workflow builds the Play release variant, signs it with a
throwaway key made on the runner, and runs the walk on an API 35 emulator. It runs for
pull requests that touch the app's screens or this tool, and on manual dispatch:

```sh
gh workflow run first-run.yml --ref <branch>
gh run download <run-id> --dir build/first-run-evidence
```

To run it yourself, use an English, unlocked API 33+ Google APIs emulator. The script
refuses anything that is not an emulator: it uninstalls Speedbreaker and replaces the
enabled Accessibility services.

```sh
python3 -m pip install pillow==11.3.0
python3 tools/first-run/walk.py --serial emulator-5554 --apk path/to/signed.apk
```

## Limits

- Reading a screen uses `uiautomator dump`, which switches Accessibility services off
  while it runs. Once the service is on, the walk reads only system screens and the app
  picker, and presses the app's one filled button by finding its colour in the
  screenshot. A screen with no filled button, or a change to that colour, needs a
  change here.
- If the emulator image's Settings layout defeats the walk, it turns the service on
  with `adb` instead and says so in `walk.json`.
- The steps match the app's wording. A pull request that changes that wording updates
  the patterns in `walk.py` with it.
- An emulator pass says nothing about Samsung or other manufacturers, restricted
  settings on sideloaded installs, or real calls. See `tools/call-fixture` for calls.
