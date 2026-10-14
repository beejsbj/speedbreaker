# Changelog

## 1.0.2 — unreleased candidate

Version code: **6**. No APK, Play release or F-Droid build has been published.

- Allow zero to four redirect destinations in Defaults and per-app Custom
  settings. Default inherits; an explicitly empty Custom set disables redirects
  for that app. Existing four-destination settings remain compatible.
- Prepare Android 16 / API 36 community and Play builds, with a clearer first-run
  Accessibility disclosure and an explicit decline action.
- Ask other apps to pause media (such as Instagram Reels) while a breath is
  showing, by requesting audio focus once per breath and releasing it when the
  breath ends. Speedbreaker plays no sound and needs no new permission; apps that
  ignore audio focus may keep playing. Never requested during call safety.
- Keep Play's external direct-tip action hidden until its destination and payment
  eligibility are verified. All features remain free, with no payment SDK.
- The optional support link on Home now opens Ko-fi (ko-fi.com/beejsbj) instead of
  Buy Me a Coffee.

The redirect change was independently reviewed and merged in [PR #10](https://github.com/beejsbj/speedbreaker/pull/10).
Store preparation and remaining verification are tracked in
[distribution status](docs/store/status.md).

## 1.0.1 — October 11, 2026

Version code: **5**.

- Suspend breaths while Android reports a ringing, dialing, active, or held call,
  including silent incoming calls displayed by SystemUI. Phone permission is
  now a setup requirement; denied or revoked access keeps breaths off. No phone
  numbers, call history, or audio are read. Communication audio is an additional
  safety signal; calls not registered with Android may still evade detection.
- Keep committed settings and pause changes visible even if the screen’s save
  coroutine is cancelled during the database write.
- Show **Active pauses** and per-app **End now** on Home. Dismissing a notification
  no longer removes the only way to end a pause early.
- Do not forward Back into a call or system screen when safety removes a breath.

- Show larger installed-app icons on the four redirect buttons, alongside names.
- Add an optional Buy Me a Coffee link at the bottom of Home. Support does not
  unlock features, and no payment SDK or Internet permission is added.

The final build passed 71 automated tests and release lint (zero errors, 32
warnings). The call fix also passed real API 35 emulator call/navigation checks. On a Samsung Galaxy
S23+, a silent incoming call interrupted the opening lock and the in-app End now
control worked after the pause notification was swiped away. See the
[1.0.1 verification record](docs/reviews/2026-10-11-1.0.1.md).

## 1.0.0 — testing prerelease

Version code: **3**. Installed-release verification: **October 11, 2026**.
Public APK availability is listed on
[GitHub Releases](https://github.com/beejsbj/speedbreaker/releases).

Independent publication review identified [incoming-call handling #6](https://github.com/beejsbj/speedbreaker/issues/6)
as a stable-release blocker, plus [settings cancellation #4](https://github.com/beejsbj/speedbreaker/issues/4)
and [pause notification dismissal #5](https://github.com/beejsbj/speedbreaker/issues/5).
These findings remain open in this APK. See the
[publication review](docs/reviews/2026-10-11-publication.md).

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

### Corrections included in this build

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
