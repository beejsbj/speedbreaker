## Goal

Build a rough, runnable phone-scale prototype of Speedbreaker's settled interaction contract so Burooj can judge the live hierarchy and feel before production UI is built.

## Core model

* The user explicitly selects distracting installed apps. Nothing is preselected; search is available; Speedbreaker and safety-critical system apps are excluded.
* Opening a selected app triggers an intervention immediately.
* Meaningful selected-app visibility counts as continuous use, including split-screen and picture-in-picture, only while the phone is interactive.
* Continuing starts a 10-minute continuous-use interval by default. The interval can be changed or disabled per app.
* Leaving for up to 10 seconds pauses the interval; returning resumes it without another opening intervention. A longer absence ends the session, so the next opening starts fresh.
* Opening and continuous-use triggers use the same intervention.

## Intervention sequence

* The overlay identifies the target app and whether it appeared on opening or after the continuous-use interval.
* Total breath duration is a global 8–60 second slider, default 12 seconds. It never varies per app.
* The first eight seconds are an invariant hard lock: show only the breathing experience; no action is available; Home, Back, and Recents do not dismiss the intervention.
* Calls, emergency/dialer UI, lockscreen, Accessibility settings, and the phone becoming non-interactive override the hard lock immediately.
* The notification shade and Quick Settings are not reachable during the eight-second lock, by design (owner decision, October 14, 2026): the lock is meant to be a hard lock from the phone itself. Calls still break through, and safety screens (Settings, Accessibility settings, dialer, emergency, lockscreen) still yield immediately if they become visible by any route.
* At eight seconds, Leave, configured redirects, and Pause become actionable. Continue becomes actionable only when the configured total breath finishes. For a 30-second breath this is 8 locked seconds plus 22 seconds before Continue unlocks.
* The fixed reflection is “What are you here for?” It requires no input and stores no answer.
* Continue and Leave share the first row with equal width and emphasis: Continue left, Leave right. Continue dismisses the overlay and starts the session interval; Leave performs Android Home.
* A second row appears only when configured and contains one to four redirect buttons with app icons and labels. Global destinations are optional (zero to four); a per-app override replaces the global set. Default inherits the global set; an explicit empty custom set shows no redirects. Redirects must be distinct non-target apps. Tapping one launches it immediately and ends the target session. Sets over four are invalid and never truncated into launch choices.
* Pause sits in the top-right corner and reports its remaining allowance.
* The surface is silent, with one subtle haptic on opening and another when choices unlock, while respecting reduced-motion settings.
* After the first eight seconds, Home, Back, and Recents yield immediately and end the target session.

## Pauses

* Each selected app receives two pause tokens per local day. This is the only daily counter; there are no usage totals or historical pause records.
* `Pause 15m · N left` consumes one token immediately, dismisses the intervention without confirmation, and suppresses that app for 15 wall-clock minutes.
* When exhausted, Pause remains visible but disabled and states when it resets.
* Active pauses and spent tokens survive reboot with their exact expiry.
* Each paused app has its own ongoing notification with `End now`.
* Pause expiry or `End now` intervenes immediately if the target is visible; otherwise normal enforcement resumes on its next opening.

## Schedules and overrides

* Speedbreaker is always active by default.
* The optional global schedule supports at most one active window per day, copyable across days and allowed to cross midnight.
* A per-app schedule replaces, rather than layers over, the global schedule.
* Outside its effective schedule an app is completely unenforced and its session is cleared. If the schedule becomes active while the app is visible, intervene immediately.
* Per-app customization is deliberately limited to continuous-use interval/on-off, schedule, and replacing the global redirect set with zero to four destinations. Breath, cooldown, intervention phrase, and pause allowance remain global and opinionated.

## Onboarding, recovery, and data

* Flow: explain the value and Accessibility behavior → affirmative consent → enable Accessibility → select apps → accept opinionated defaults.
* Disclose plainly that Speedbreaker observes only selected-app visibility locally and temporarily prevents navigation from dismissing the intervention.
* Request neither Usage Access nor a separate overlay permission.
* Show prominent Samsung battery/background guidance without blocking setup.
* If Accessibility is disabled, show a prominent inactive/repair state, never imply protection is active, keep settings editable, and link directly to Android Accessibility settings.
* Persist only settings, current-local-day pause state, and a running breathing total (total and current-local-day breath time, plus breath count; see the October 16 breathing-total correction; amended again by the November 12 breathing-statistics correction). No per-app or time-stamped usage history, analytics, reflection data, or cloud path exists in V1.
* V1 is a sideloaded APK for Burooj's Galaxy S23+; Play Store acceptance is not a pre-trial gate.

## Explicitly absent

Cumulative usage, daily usage budgets or resets, over-budget state, escalating 5/10/20/40 ladders, mercy allowance, Usage Access, per-app usage counters or time-stamped history (the single breathing total is allowed; counts of endings and chosen detours and seven daily totals were allowed on November 12), multiple schedule windows per day, and time-dependent strictness profiles.

Future note only: V2 may allow stricter policy during selected windows, such as a longer breath and shorter continuous-use interval before sleep. Do not add hidden V1 state or settings for it.

## Evidence and constraints

* BJS-347 proved selected-window observation and a service-owned accessibility overlay on the Galaxy S23+, including foreground, split-screen, picture-in-picture, Home/Recents/Back persistence, screen-interactive gating, and dialer yield.
* Before the one-day trial, physically prove reboot reconnection, Samsung idle/battery survival, normal incoming-call yield, immediate safety escape, screen-lock yield, Accessibility-disable escape, and navigation release exactly after eight seconds.
* one sec's first-party Android behavior validates the opening + interval + quick-return product shape. Research handhold: archive tag `archive/research/bjs-346-one-sec`, commit `51d3835`, `research/one-sec-android.md`.
* The recovered AI Studio export is visual reference only. Do not extend its source structure or shallow state model.
* Production architecture is settled in BJS-345; this artifact is throwaway interaction evidence.

## Prototype focus

Let the prototype answer presentation questions through live reaction: whether the eight-second hard lock feels proportionate; whether the staged action reveal is legible; whether two action rows plus top-right Pause remain calm; and whether setup, overrides, schedules, pause exhaustion, disabled-service recovery, calls, and expiry transitions are understandable.

## Done when

* A runnable phone-scale prototype covers the complete settled contract and contains no cumulative-usage concepts or copy.
* It makes trigger type, timers, schedule state, cooldown, active pause, remaining tokens, and Accessibility status inspectable so state transitions can be judged.
* The artifact path and commit are recorded here.
* Burooj has driven the live flow and the resulting presentation decisions and verdict are recorded here.

## October 11 release clarification

Burooj accepts One Hand Operation+ being unavailable while the Speedbreaker
overlay is visible. OHO compatibility is not a release defect. The delivery
goal is a signed, shareable app; Burooj will configure his own targets and
redirects. His personal full-day trial does not gate delivering that artifact.
Device observations and untested recovery cases must still be reported accurately.

## 1.0.1 call-safety and pause-control clarification

**Superseded in part by 1.0.6 (owner decision, November 11, #70):** Phone permission is
optional. Without it Telecom is not asked and only the audio mode is read, so breaths
run and a call ringing without a ringtone (silent, vibrate or Do Not Disturb) is not seen. The rest of this section
describes behaviour with the permission granted.

The service observes visible app identities, including non-target safety screens,
without traversing screen text. Phone permission is now required before breaths
can run. It reads Android's aggregate call-state boolean and current audio mode;
no numbers, call history, notification contents, or audio are read or stored.
Denied/revoked access or observable query failure suspends breaths and preserves
settings. Android-reported calls bypass the eight-second lock through each event,
choice observation, and the 250 ms ticker. That interval is not a guaranteed
latency deadline. Calling apps that do not register with Android may not be
detected while ringing; the original broad “Calls” sentence is not a guarantee
for every third-party app. When call safety clears, a still-visible target starts
a fresh breath. A safety dismissal never forwards Back into the exposed call UI.

Home now shows active pauses with an End now action for each app. These controls
remain available if Android 14+ lets the user dismiss the pause notification.

## 1.0.2 media quieting

When a breath starts, Speedbreaker requests transient audio focus
(`AUDIOFOCUS_GAIN_TRANSIENT`) so well-behaved players pause. The surface itself
stays silent. Audio focus is phone-wide, so this also pauses background music or
podcasts for the length of the breath; transient rather than permanent focus lets
them resume afterwards instead of stopping for good. The target stops on its own
once Leave or Redirect sends it to the background. Focus is requested once per
breath, never while call safety is active, and abandoned whenever the breath ends
for any reason.

Some players ignore audio focus (Instagram Reels on the S23+ keeps playing). About
half a second after the request, if `AudioManager.isMusicActive()` is still true
and the music stream is not already muted, Speedbreaker mutes `STREAM_MUSIC`
with `ADJUST_MUTE` and no UI flag, and remembers that it did. Every end path
abandons focus and then unmutes, but only if the stream is still muted, so a
volume change by the user during the breath is respected. Alarm, ring,
notification and voice streams are never touched, and nothing is muted during call
safety. A refused mute (`SecurityException`) is ignored. A small local flag records
that Speedbreaker muted the stream; when the service connects and the flag is
still set (the process died mid-breath), it unmutes and clears the flag. Muting
needs no new permission. Device behavior with Reels is unverified until Burooj
tests this build.

## October 16 quick-return owner correction

Burooj shortened the quick-return grace from 60 to 10 seconds on October 16,
2026: switching to another app after Continue and coming back more than ten
seconds later is a fresh opening and starts a breath. Brief hops (copying a
link, a quick reply) stay free.

## October 11 redirect-count owner correction

Burooj’s [issue #9](https://github.com/beejsbj/speedbreaker/issues/9), opened
October 11 at 18:21 UTC, explicitly allows fewer than four redirects. This
supersedes the earlier exactly-four contract for the next Android candidate.
One, two, three, and four selections can be saved globally or per app; zero
remains optional. Choosing a redirect app as a target removes it from the global
and custom redirect lists while preserving the remaining destinations.

Existing four-app settings keep their order and meaning. Per-app Default
continues to inherit, Custom replaces the entire set, and clearing Custom keeps
an explicit empty override. Breath timing, navigation, safety escape, schedules,
pauses, local privacy, and all-free behavior retain their existing contracts.

## October 16 breathing-total owner correction

Burooj asked on October 16, 2026 for a simple way to see how many seconds he has
breathed. This amends the earlier "no usage history" rule narrowly. Home now shows
the total breath time and breath count, plus today's time, with a confirmed
**Reset breathing total** action.

The Home redesign of October 17 settles where it sits. While breaths are working and
apps are chosen, the page opens with a quiet "On" status (adding "resting until 9 AM"
when every chosen app is outside its schedule, or "pauses need notifications" when
Pause cannot start) and the total as the headline: "18 minutes", then "94 breaths ·
2 minutes today". Before the first breath it reads "No breaths yet". When a repair
notice leads the page, the total is one line after it ("18 minutes breathed so far.").
The reset action lives in the footer and appears only once there is something to reset.

Only four aggregates are stored on the device: total milliseconds, total breaths,
today's local date, and today's milliseconds. A breath counts once, when it ends by
any path, for the time its overlay was shown capped at its configured length;
breaths shown for under one second (such as transient safety restarts) are not
counted. "Today" restarts at local midnight. There is no per-app figure, timestamp,
list of breaths, or other history; the total is never sent anywhere, and a failed
write to it never affects enforcement.

## October 17 in-app-updater owner correction

Burooj asked on October 17, 2026 for in-app updates, like the Nuvio Android app, so
people need not fetch each new APK from GitHub by hand. This amends the "no cloud
path" and offline rules for one build only.

The APK published on GitHub Releases (the `github` build) can check, download and
install its own updates. The F-Droid (community) and Google Play builds cannot:
F-Droid forbids self-updaters and Play forbids updating outside Play. They keep no
Internet permission and no updater code.

* Home's footer gains **Check for updates**. It asks GitHub's public API for the
  latest release of `beejsbj/speedbreaker`, with no account, token or identifier;
  the only header set is `User-Agent: Speedbreaker/<version>`. Drafts and
  prereleases are ignored; a `vX.Y.Z` tag newer than the installed version is
  offered.
* Defaults gains **Check for updates automatically**, off by default. When on, the
  check runs at most once a local day when the app UI opens. No background job,
  WorkManager, alarm or notification exists.
* An offered update is a calm card on Home: "Speedbreaker 1.0.4 is ready", with
  **What's new** (the release notes as plain text), **Update** and **Not now**.
* **Update** asks once for Android's **Install unknown apps** permission if it is
  missing, links straight to that setting, and continues when the person returns.
  It downloads `Speedbreaker-X.Y.Z.apk` to the app cache with progress and
  **Cancel**, verifies its SHA-256 against the release's `.apk.sha256` and its
  package and version, and hands it to Android's PackageInstaller. Android asks
  for confirmation and accepts it only if it carries the installed app's signing
  key. The cached file is deleted after hand-off, cancel or failure.
* Nothing checks, downloads or prompts while a breath is showing. A breath that
  starts mid-download stops it; Android's install prompt waits until the breath
  ends. The updater lives in the app UI only; the Accessibility service merely
  reports whether a breath is on screen.
* Errors are quiet and plain ("Couldn't reach GitHub. Try again later."). A failed
  automatic check shows nothing.
* The GitHub build stores two more local values: the automatic-check switch and the
  date of the last automatic check. Nothing about the person or their use is sent.

Settings, breath timing, safety, pauses, schedules, the breathing total and all
local-data rules are unchanged. The installed-over update flow on a phone is
unverified until Burooj tests a build that has a newer release to update to.

## November 12 Home and Settings owner correction

Burooj on November 11, 2026, about the single page the app opened on: "it feels like
a settings page and a home page almost and then inside defaults exists the check for
updates I mean automatic updates and then at the bottom in this another uh presentation
typographical hierarchies, check the button for check for updates. I think this stuff
needs to be modernized." Shown three structures (Home and Settings behind a gear; three bottom
tabs; one tidied page), he chose the first: "I agree with A."

This supersedes the October 17 placement of everything on Home, and the footer:

* Home says what is happening: the status, the breathing total, running pauses, an
  offered update, the setup steps or the repair for a fault, and the chosen apps. A
  gear in its top bar opens Settings.
* Settings holds everything a person sets or checks, as ordinary rows in four groups:
  Defaults (Breath length, Schedule, Detours), Reliability (Notifications, Phone
  permission, Battery and background), Updates (GitHub build only) and About (Help,
  Privacy policy, support, Reset breathing total). The quiet text links at the foot of
  Home are gone.
* The single Defaults page is split: Breath length and Schedule each have a page, and
  Detours opens the picker. "Default" keeps its meaning on an app's own page.
* **Check for updates** and the automatic-check switch sit together under Updates. The
  answer to a check ("Checking…", "You have the latest version", or that GitHub could
  not be reached) shows beneath them; Home only shows the update card when there is an
  update to offer or one under way.
* A fault never hides behind the gear: Accessibility off, a failed connection, and
  "Pause 15 min needs notifications" still show on Home.

What is stored, what is sent, breath timing, safety, pauses and schedules are unchanged.
How the breathing total is presented is still open: "I think the breathing total could
be more prettier presented stats."

## November 12 breathing-statistics owner correction

Burooj on November 12, 2026, after the Home and Settings split, tracked as
[#65](https://github.com/beejsbj/speedbreaker/issues/65):

* "I think the breathing total could be more prettier presented stats."
* "maybe like a visualization of the data as well, instead of just raw number. And then
  maybe even showing the detours like which apps I chose to detour to as a counter or
  something? How many times did I choose to leave? How many times I choose to continue?"
* On the status line: "I don't like that "on". Could we be visualized better?"
* On the figures: "I also don't like 18 "minutes", "breadths", and two "minutes
  today". Like the text makes it feel a bit amateur".
* On Home's weight: "there should be quite a lot of as much as A on home, but not that
  much, but also a detailed page as well", "detours taken can be smaller", "We can keep
  the weak bars in the detailed view", "we can start from zero".
* The choice of header: "I like one figure labelled and the status pills dot can instead
  be the breathing mark. Clicking on the status pill that says Active3 Apps scrolls me
  down to the show me the list of apps."

This amends the October 16 rule that only four aggregates are stored, and the non-goals
above that exclude per-app counters and history. It was his decision, made after being
told which lines it reverses.

What is now stored beside the breathing total, in a second local record:

* Five counts of how counted breaths ended: Left, Continued, Took a detour, Took 15 min
  off (the Pause 15 min button), and Ended another way (nothing the person did: safety,
  the lock screen, schedule, a settings change, reset). Left covers the Leave button and
  going Home, Back or to another app once the first eight seconds allow it. Burooj
  confirmed on November 12 that the eight-second hold itself is right as it is ("Its
  right.") and chose to count those exits as Left.
* For each app that is a detour somewhere (the default set or any app's own), how many
  times it was taken. The count is by destination only. When an app stops being a detour
  everywhere, its count is deleted; the count of detours taken stays.
* Milliseconds breathed on each of the seven local days ending with the latest breath,
  and the local day counting began.

Still not stored, in any version: a field naming the chosen app a breath was for, any time
of day, a list of breaths, or a day older than seven days (dropped when the app next
starts or counts a breath). One inference remains and is stated in the privacy policy: an
app that is a detour for only one chosen app has a count that is also a count for that pair. Counting starts from zero at the update;
the existing total and breath count are kept, so at first the endings add up to less than
the breath count. **Reset breathing total** clears both records. A failed write to either
never affects enforcement.

How it is shown:

* Home, while breaths work: a status pill with the breathing mark ("Active · 3 apps", or
  "Resting until 9 AM" with the mark still), the total as one large figure with a small
  unit, Breaths and Today as labelled figures, then the "After a breath" card (a thin
  split bar, the counts, the three most-taken detours and "+N more"). The pill brings the
  apps into view; the card opens Your breaths.
* Your breaths: the figures, every ending with its count, every chosen detour with its
  count, and seven day bars.
* While breaths are off or no app is chosen, the total is one row after the repair
  ("Your breaths", "18 minutes breathed so far."), and it still opens the page.
* The statistics are descriptive. No percentages, streaks, scores, goals or praise, and
  no ending is coloured or worded as better than another. The mark holds still when the
  system's animations are off.

The welcome disclosure, privacy policy, README, glossary and store description say this.
People who accepted the earlier disclosure are not shown the new wording again.

## November 12 single-control owner correction

Burooj later on November 12, 2026, after using 1.0.7 on his phone: "The defaults breadth length
and schedule don't need to be in submenus. They are only one like they are only one form
input, like one range or toggle each. Pointless. Check auto toggle is in main screen. Both
can be in main settings."

This amends the split of the Defaults page made earlier the same day:

* Breath length is its slider, set in place on a tile in Settings.
* Schedule is its Always on / Scheduled choice, set in place; when scheduled, the week
  editor opens on a tile beneath it.
* Detours still opens the picker.
* The rule this leaves: a setting that is one control is set where it is listed. A row
  opens a page only when the thing needs a screen.

Stored data, breath timing and per-app settings are unchanged.

