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
* Persist only settings, current-local-day pause state, and a running breathing total (total and current-local-day breath time, plus breath count; see the October 16 breathing-total correction). No per-app or time-stamped usage history, analytics, reflection data, or cloud path exists in V1.
* V1 is a sideloaded APK for Burooj's Galaxy S23+; Play Store acceptance is not a pre-trial gate.

## Explicitly absent

Cumulative usage, daily usage budgets or resets, over-budget state, escalating 5/10/20/40 ladders, mercy allowance, Usage Access, per-app usage counters or time-stamped history (the single breathing total is allowed), multiple schedule windows per day, and time-dependent strictness profiles.

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
