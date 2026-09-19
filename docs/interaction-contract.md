## Goal

Build a rough, runnable phone-scale prototype of Speedbreaker's settled interaction contract so Burooj can judge the live hierarchy and feel before production UI is built.

## Core model

* The user explicitly selects distracting installed apps. Nothing is preselected; search is available; Speedbreaker and safety-critical system apps are excluded.
* Opening a selected app triggers an intervention immediately.
* Meaningful selected-app visibility counts as continuous use, including split-screen and picture-in-picture, only while the phone is interactive.
* Continuing starts a 10-minute continuous-use interval by default. The interval can be changed or disabled per app.
* Leaving for up to 60 seconds pauses the interval; returning resumes it without another opening intervention. A longer absence ends the session, so the next opening starts fresh.
* Opening and continuous-use triggers use the same intervention.

## Intervention sequence

* The overlay identifies the target app and whether it appeared on opening or after the continuous-use interval.
* Total breath duration is a global 8–60 second slider, default 12 seconds. It never varies per app.
* The first eight seconds are an invariant hard lock: show only the breathing experience; no action is available; Home, Back, and Recents do not dismiss the intervention.
* Calls, emergency/dialer UI, lockscreen, Accessibility settings, and the phone becoming non-interactive override the hard lock immediately.
* At eight seconds, Leave, configured redirects, and Pause become actionable. Continue becomes actionable only when the configured total breath finishes. For a 30-second breath this is 8 locked seconds plus 22 seconds before Continue unlocks.
* The fixed reflection is “What are you here for?” It requires no input and stores no answer.
* Continue and Leave share the first row with equal width and emphasis: Continue left, Leave right. Continue dismisses the overlay and starts the session interval; Leave performs Android Home.
* A second row appears only when configured and contains exactly four redirect buttons. Four global destinations are required; a per-app override replaces all four. Redirects must be non-target apps. Tapping one launches it immediately and ends the target session.
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
* Per-app customization is deliberately limited to continuous-use interval/on-off, schedule, and replacing all four redirects. Breath, cooldown, intervention phrase, and pause allowance remain global and opinionated.

## Onboarding, recovery, and data

* Flow: explain the value and Accessibility behavior → affirmative consent → enable Accessibility → select apps → accept opinionated defaults.
* Disclose plainly that Speedbreaker observes only selected-app visibility locally and temporarily prevents navigation from dismissing the intervention.
* Request neither Usage Access nor a separate overlay permission.
* Show prominent Samsung battery/background guidance without blocking setup.
* If Accessibility is disabled, show a prominent inactive/repair state, never imply protection is active, keep settings editable, and link directly to Android Accessibility settings.
* Persist only settings and current-local-day pause state. No usage history, analytics, reflection data, or cloud path exists in V1.
* V1 is a sideloaded APK for Burooj's Galaxy S23+; Play Store acceptance is not a pre-trial gate.

## Explicitly absent

Cumulative usage, daily usage budgets or resets, over-budget state, escalating 5/10/20/40 ladders, mercy allowance, Usage Access, usage counters/history, multiple schedule windows per day, and time-dependent strictness profiles.

Future note only: V2 may allow stricter policy during selected windows, such as a longer breath and shorter continuous-use interval before sleep. Do not add hidden V1 state or settings for it.

## Evidence and constraints

* BJS-347 proved selected-window observation and a service-owned accessibility overlay on the Galaxy S23+, including foreground, split-screen, picture-in-picture, Home/Recents/Back persistence, screen-interactive gating, and dialer yield.
* Before the one-day trial, physically prove reboot reconnection, Samsung idle/battery survival, normal incoming-call yield, immediate safety escape, screen-lock yield, Accessibility-disable escape, and navigation release exactly after eight seconds.
* one sec's first-party Android behavior validates the opening + interval + quick-return product shape. Research handhold: branch `research/bjs-346-one-sec`, commit `51d3835`, `research/one-sec-android.md`.
* The recovered AI Studio export is visual reference only. Do not extend its source structure or shallow state model.
* Production architecture is settled in BJS-345; this artifact is throwaway interaction evidence.

## Prototype focus

Let the prototype answer presentation questions through live reaction: whether the eight-second hard lock feels proportionate; whether the staged action reveal is legible; whether two action rows plus top-right Pause remain calm; and whether setup, overrides, schedules, pause exhaustion, disabled-service recovery, calls, and expiry transitions are understandable.

## Done when

* A runnable phone-scale prototype covers the complete settled contract and contains no cumulative-usage concepts or copy.
* It makes trigger type, timers, schedule state, cooldown, active pause, remaining tokens, and Accessibility status inspectable so state transitions can be judged.
* The artifact path and commit are recorded here.
* Burooj has driven the live flow and the resulting presentation decisions and verdict are recorded here.