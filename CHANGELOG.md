# Changelog

## 1.0.7 — November 12, 2026

Install over 1.0.6; your settings and your breathing total carry over.

- **Home and Settings are now two pages.** Home says what is happening: whether
  Speedbreaker is active, how much you have breathed, and your apps. A gear at the top
  opens **Settings**, which holds everything you set or check: Breath length, Schedule
  and Detours; Notifications, Phone permission and the battery tip; updates; Help,
  Privacy policy and Reset breathing total. The quiet links at the foot of Home are gone.
- **A new look for Home.** A moss pill with a slowly breathing mark says "Active · 3
  apps" (or "Resting until 9 AM"); tapping it brings your apps into view. The total is
  one large figure, with Breaths and Today beside it.
- **See what you did after a breath.** A new card on Home counts how many breaths ended
  with you leaving, continuing, taking a detour, or taking 15 minutes off, and names the
  detours you took most. It opens **Your breaths**, with every detour and the time you
  breathed on each of the last seven days. Counting starts at zero when you install this
  version, so at first these add up to less than your breath count.
- **Lists are easier to scan.** Each row is its own tile, and Settings rows have icons.
- **Check for updates** and the automatic check now sit together under **Settings →
  Updates** (GitHub APK only), and the answer to a check appears right under them.

**Privacy:** to show the new card, Speedbreaker now keeps a few more running totals on
your phone: how many breaths ended each way, how often you took each chosen detour, and
the time breathed on each of the last seven days. No list of breaths and no time of day
is kept, a detour you remove takes its count with it, and **Reset breathing total**
clears all of it. Nothing is sent anywhere. If an app is a detour for only one of your
chosen apps, its count is also a count for that pair. The
[privacy policy](docs/privacy.md) has the details.

Version code: **11**.

## 1.0.6 — November 11, 2026

Install over 1.0.5; your settings carry over.

- **Breaths work with Accessibility alone.** Phone permission is now optional. Without
  it, a breath still steps aside when you are on a call or a ringtone is playing, but
  a call that rings without a ringtone (silent, vibrate or Do Not Disturb) can stay
  hidden behind a breath for its length. Allow it under **Reliability → Phone
  permission** on Home to close that gap. If you already allowed it, nothing changes.
- **A clearer first open.** Two short pages (what it does, then the Accessibility
  disclosure), then two steps on Home: turn it on, choose apps. The Accessibility
  button asks Android to scroll to and highlight Speedbreaker, and the app opens again
  by itself once the service is on.
- **New names.** The entry in Android's Accessibility list is now **Speedbreaker**
  (it was "Speedbreaker attention pauses"); the service stays on through the update.
  **Redirects** are now **Detours**. "Pause" now only means the 15-minute break from
  breaths.
- **Help** at the foot of Home: how a breath goes, what to check when one doesn't
  appear, calls, check-ins, Pause, and how to switch it off.
- Plainer wording throughout, a **Done** button in the app picker, and a pause
  notification that says "Paused for <app>" and when breaths return.
- The privacy policy and guides were corrected: the notification shade can't be
  pulled over a breath in its first eight seconds, and window ids and types (not
  contents, positions or sizes) are read.

Version code: **10**. [GitHub release](https://github.com/beejsbj/speedbreaker/releases/tag/v1.0.6), source `06e32a7`, APK SHA-256 `aee4d8d527bccbda1e6b823cc59fd6462fcff8b94300efe777a7bae7f67574e0`.

## 1.0.5 — October 17, 2026

- The app icon is now the Speedbreaker mark — two open arcs and a still center —
  on the brand's dark ground, replacing the placeholder triangle. On Android 13+
  it follows your themed-icon colours.

Version code: **9**.

## 1.0.4 — October 17, 2026

- In-app updates for the GitHub APK. **Check for updates** at the foot of Home asks
  GitHub for the latest release; a newer one appears as a calm card with What's new
  and Update. Update downloads the APK with progress and Cancel, checks it against
  the release's SHA-256 file, and hands it to Android's installer, which asks you to
  confirm and accepts only the same developer's signature. If Android needs the
  **Install unknown apps** permission first, the card explains and links there, and
  the update continues when you come back. An optional **Check for updates
  automatically** switch in Defaults, off by default, checks at most once a day when
  you open the app. Nothing checks, downloads or prompts during a breath.
- The GitHub APK is now its own build: the community build plus the updater, the
  only build with Internet and install permissions. Requests carry no account or
  identifier, only a `Speedbreaker/<version>` user agent. F-Droid (community) and
  Google Play builds have no updater and no Internet permission.
- Installs of 1.0.3 or earlier have no updater: install 1.0.4 over them by hand once.

## 1.0.3 — October 17, 2026

Version code: **7**. [GitHub release](https://github.com/beejsbj/speedbreaker/releases/tag/v1.0.3), source `be9b746`, APK SHA-256 `4eb6c527cb95cfd1bbe162bbcacf75d2fb0c9102c4b4eaa4e5b2b895b54633c4`.

- Redesigned Home: when Speedbreaker is on, a calm "On" status and how long you
  have breathed lead the page, replacing the "On for N apps" title and the
  breath-length notice. The status says when every chosen app is resting until its
  next scheduled hours, or that Pause needs notifications. Needs-attention states
  keep their repair notice first and a one-line total after it. Defaults reads as
  the breath length with schedule and redirects beneath; the Samsung battery tip
  moved into the Reliability row; support, privacy and Reset breathing total sit in
  a quiet footer.
- Home shows how long you have breathed ("18 minutes", "94 breaths · 2 minutes
  today"), with a confirmed Reset breathing total. A breath counts once, when it
  ends, for the time it was shown up to its configured length; breaths under one
  second are ignored. The device keeps only four running numbers, never per-app or
  time-stamped history, and sends nothing anywhere. Privacy docs updated to match.

## 1.0.2 — October 16, 2026

Version code: **6**. [GitHub release](https://github.com/beejsbj/speedbreaker/releases/tag/v1.0.2), source `94cbeaf`, APK SHA-256 `8237f658d69bbb7b364035f501faf16eae4ca5501094d75d8b3fec376f9d5178`. Not on F-Droid or Google Play.

- Keep the notification shade and navigation blocked for the full eight-second
  hold (owner decision), and stop volume popups or notifications from counting as
  leaving, so the breath no longer vanishes before its choices.
- Fill the breathing ring continuously instead of in tick-sized jumps.

- Shorten the quick-return grace after Continue from 60 to 10 seconds: coming
  back to a chosen app more than ten seconds later starts a fresh breath.
- Allow zero to four redirect destinations in Defaults and per-app Custom
  settings. Default inherits; an explicitly empty Custom set disables redirects
  for that app. Existing four-destination settings remain compatible.
- Prepare Android 16 / API 36 community and Play builds, with a clearer first-run
  Accessibility disclosure and an explicit decline action.
- Ask other apps to pause media (such as Instagram Reels) while a breath is
  showing, by requesting transient audio focus once per breath and releasing it
  when the breath ends, so background music resumes afterwards. For apps that
  ignore audio focus, mute the music stream half a second in and unmute it when
  the breath ends (recovering a stuck mute after a crash). Speedbreaker plays no
  sound and needs no new permission. Never touched during call safety.
- Add logcat-only diagnostics (`adb logcat -s Speedbreaker:I`) that explain why a
  breath started, ended, or was skipped when a chosen app came into view, with the
  safety, service, overlay and media steps behind it. Reason codes and numbers only;
  nothing is stored or sent, and the only package names are your chosen targets.
  Enforcement behavior is unchanged.
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
