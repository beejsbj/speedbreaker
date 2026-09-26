# Calm UI redesign

A visual and UX pass over the overlay, choices, settings, and setup. The
enforcement engine, persistence, and the Accessibility host are unchanged; the
overlay keeps its existing `BreakerOverlay` signature.

## Principles

- **The breath is the whole screen for the first eight seconds.** Then the orb
  draws back, the reflection arrives, and the choices rise — one idea at a time
  (staggered 150 / 650 / 950 ms; Pause fades in last).
- **Ink and ivory, dark by default.** Two neutrals carry everything. Moss marks
  "on", brick marks "needs repair"; nothing else is colored. The app and overlay
  are always dark; the ivory scheme stays in `Theme.kt` for a later choice.
- **Serif for what asks something of you** (the reflection, page titles, the
  consent), system sans for everything operational.
- **Say what is true in plain words.** Rows read out their state ("Check-in
  every 10 min · weekdays · 9 AM–5 PM") so nothing has to be opened to be known.
- **Continue and Leave carry equal weight.** Neither is styled as the right answer.

## Overlay

- Numeric hard-lock countdown replaced by a thin progress arc around the orb
  and a "Breathe in / Breathe out" cue synced to the orb (4 s each way,
  anchored to the breaker's start). Reduced motion: still orb, "Breathe slowly".
- Continue's wait lives in the button itself ("Continue in 9s").
- Trigger reads as "Opening" / "Checking in" under the app name.
- Redirects are icon tiles under an "Or go to" label.
- Pause stays top-right; exhausted and notification-less states are explicit.
- Layout: stacked in portrait (choices within thumb reach), side-by-side in
  landscape, scrollable at large text sizes; short split-screen windows shrink
  the orb and drop the cue.

## Settings and setup

- **Welcome** is one page: what it does, what it sees, what the first eight
  seconds do, what it keeps.
- **Home** is also the setup checklist: its title is the status ("Off for now",
  "Needs attention", "Ready when you are", "On for 3 apps"), followed by the one
  next action, then Apps, Defaults, and Reliability.
- **Defaults** — breath slider with a large readout, redirects as four visible
  slots, schedule as Always on / Scheduled.
- **Schedule as hours groups.** A week reads as groups of days sharing one set
  of hours ("9 AM to 5 PM · M T W T F", "10 AM to Midnight · S S"). Changing a
  group's hours changes all its days, so there is nothing to copy. Tapping a
  day moves it into that group; outlined days belong to another group; plain
  days are off, and a footer says which. "Other hours" starts a new group.
- **Time picker** — the Material 3 clock dial in a dialog, with a keyboard
  toggle to type the time instead. An end of 12 AM means end of day.
- **App** — check-in toggle and interval, schedule Default / Always on /
  Custom, redirects Default / Custom, a note on what stays global, and
  "Stop pausing <app>".
- **Pickers** are full screens with search; the redirect picker shows the four
  slots filling as you choose.

## Behavior changes worth checking

These are presentation-level, but they change what a tap does:

1. A new schedule (global or per-app Custom) starts at weekdays 9 AM–5 PM
   instead of an empty week, which silently enforced nothing.
2. Times are set with the Material time picker (dial or typed) instead of a
   raw HH:MM field. Picking 00:00 as an end time stores 24:00 (end of day),
   which covers the same minutes.
3. Per-app Custom schedule starts from the global schedule when one exists.
4. The redirect picker is a full-screen page instead of a dialog; Clear remains.
5. "Pause notifications" asks for permission once, then opens system settings.
6. App settings gained "Stop pausing <app>" (same effect as unchecking it in
   the picker).
7. The app and overlay are always dark.
8. The per-day Copy action is gone; shared hours are one group instead.

Unchanged and deliberately out of scope: the choice-acknowledgment toast,
haptics, One Hand Operation compatibility.

## Material 3 Expressive, restrained

Four Expressive behaviors, chosen because they act on feel rather than look:

- **Spring motion** — the standard scheme's springs (`theme/Motion.kt`)
  replace timed curves for the unlock: the orb draws back on a slow spatial
  spring, the choices' block springs open, and each idea fades in on an
  effects spring. Stagger is kept by holding each target back briefly. The
  bouncier *expressive* scheme is not used. The breath itself stays on its
  eased 4-second rhythm: it is a breathing pace, not a UI transition.
- **Press shape morph** — Continue, Leave, Pause, redirect tiles, and the
  shared actions square off slightly under the finger and spring back.
  Continue and Leave morph identically.
- **Connected button groups** replace segmented buttons for mode choices.
- **Shape-morphing loading indicator** on "Opening your settings…", slow and
  in ink.

Left out on purpose: morphing the breath orb, a wavy breath ring, dynamic
color, and hero-scale shapes — each would compete with the breath.

These are built on stable Compose (material3 1.4.0, the version even the
2026.09 BOM pins). The real Expressive components ship only in material3
1.5.0-alpha, which would move the whole Compose stack to 1.13.0-alpha01,
including the overlay that passed phone acceptance. Spring values are
the published `StandardMotionTokens`. When 1.5 is stable, `MotionScheme`,
`ButtonGroup`, button `shapes`, and `LoadingIndicator` can replace
`Expressive.kt` and `Motion.kt` directly.

## Screenshots

Rendered headlessly from `Previews.kt` by `GalleryScreenshotTest` (Robolectric
native graphics + Roborazzi) at 412×915 dp, xxhdpi. App icons render as
monograms there; on a phone they are the real icons. The breath is held still
at the instant each sample implies (`LocalStillBreath`), since an endless
animation never lets the test clock settle.

```sh
export ANDROID_SDK_ROOT=/usr/lib/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:testDebugUnitTest --tests '*GalleryScreenshotTest*' \
  --no-daemon --no-configuration-cache --max-workers=1
```

| Overlay | |
|---|---|
| ![](screenshots/overlay-01-breath.png) Hard lock: breath only | ![](screenshots/overlay-02-choices-waiting.png) Choices; Continue waiting |
| ![](screenshots/overlay-03-choices-ready.png) Breath complete | ![](screenshots/overlay-04-checkin-no-redirects-no-pauses.png) Check-in, no redirects, pauses spent |
| ![](screenshots/overlay-07-large-text.png) 160% text | ![](screenshots/overlay-08-reduced-motion-breath.png) Reduced motion |

![](screenshots/overlay-06-landscape.png)

| Setup | |
|---|---|
| ![](screenshots/setup-00-loading.png) Loading, mid-morph | |
| ![](screenshots/setup-01-welcome.png) Welcome | ![](screenshots/setup-02-home-off.png) Accessibility off |
| ![](screenshots/setup-03-home-ready-no-apps.png) Connected, no apps | ![](screenshots/setup-04-app-picker.png) Choose apps |

| Settings | |
|---|---|
| ![](screenshots/settings-01-home-on.png) Home | ![](screenshots/settings-02-home-needs-attention.png) Needs attention |
| ![](screenshots/settings-03-defaults.png) Defaults | ![](screenshots/settings-04-app.png) One app |
| ![](screenshots/settings-05-redirect-picker.png) Redirect picker | ![](screenshots/settings-07-home-large-text.png) 160% text |
| ![](screenshots/settings-08-schedule-hours.png) Hours groups | ![](screenshots/settings-09-time-picker.png) Time dial |
| ![](screenshots/settings-10-time-typed.png) Typed time | |
