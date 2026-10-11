# Changelog

## 1.0.0 — initial sideload release build

Version code: **3**. Installed-release verification: **October 11, 2026**.
Public APK availability is listed on
[GitHub Releases](https://github.com/beejsbj/speedbreaker/releases).

### Included

- Breathing pauses when opening user-selected apps, with optional continuous-use
  check-ins. No apps are preselected.
- A shared 8–60 second breath, twelve seconds by default. Leave, redirects,
  and Pause unlock at eight seconds; Continue unlocks when the breath finishes.
- Four optional redirect destinations, global or replaced per app.
- Two fifteen-minute pauses per app per local day, with an **End now**
  notification action and persisted allowance and expiry state.
- Weekly active hours, overnight windows, and per-app schedule overrides.
- A sixty-second quick-return window and visible-use tracking for split-screen
  and picture-in-picture.
- A dark interface, breathing animation, reduced-motion support, and layouts
  for landscape and large text.
- Local settings and pause state, without accounts, analytics, usage history,
  or cloud sync.

### Final release corrections

- Choice controls become usable at eight seconds without an extra entrance
  animation delay.
- Picker search and selection remain reachable in short landscape windows
  with large text.
- Release packaging resolves caller-supplied paths consistently.

### Verification and limits

The recorded release run passed 43 automated tests. Release lint completed with
zero errors and 31 warnings. Signature verification passed.

On a Samsung Galaxy S23+, the installed release passed controlled opening,
Continue, timed check-in, Leave, and Android Settings escape checks. Calls,
reboot/recovery, extended idle, and other manufacturers were not tested on this
release. One Hand Operation+ gestures are unavailable while the overlay is
visible, as documented.

See the [release verification record](docs/reviews/2026-10-11-release.md) for
source and artifact hashes, test scope, and historical delivery details.
