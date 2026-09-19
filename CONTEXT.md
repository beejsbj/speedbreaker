# Speedbreaker

Speedbreaker is a personal Android attention aid that inserts deliberate friction between the impulse to open a distracting app and the choice to continue using it.

## Product direction

- The production interface uses Material 3 Expressive as its component and
  motion foundation, restrained into a minimal, calming ink-and-ivory visual
  language. Expressive means purposeful hierarchy, shape, type, and motion—not
  visual noise, bright decoration, or gamification.
- The breathing pause remains the visual center. Reflection and choices arrive
  progressively instead of competing for attention at once.
- Production layouts must adapt cleanly across portrait, landscape, split
  screen, and accessibility text sizes. The throwaway enforcement probe is not
  a visual reference or acceptance target.
- Redirect is part of the production Speedbreaker choice set. Its absence from
  the enforcement probe is deliberate: the probe isolates Android interception,
  continuous-use timing, safety, and recovery before product features are
  layered on.

## Language

**Target app**:
An installed app chosen for mediation, with its own settings inherited from global defaults. Speedbreaker, the launcher, Settings, input methods, permission controllers, and System UI cannot be target apps.
_Avoid_: Blocked app, distraction app

**Speedbreaker**:
A configurable timed pause presented before access to a target app, ending in an explicit Continue, Leave, or Redirect choice.
_Avoid_: Intervention, blocker, lockout, interruption

**Opening trigger**:
The condition that starts a Speedbreaker when a target app is newly opened outside its cooldown.
_Avoid_: App block

**Continuous-use trigger**:
The condition that starts a Speedbreaker when a target app remains visibly in active use for its configured interval. The interval is adjustable or can be disabled per app. There is no cumulative-daily trigger.
_Avoid_: Screen-time limit

**Active use**:
Screen-on time when a target app is foregrounded or visibly present in picture-in-picture or split-screen, and the phone is interactive. Background activity does not count.
_Avoid_: Background time, process runtime

**Cooldown**:
The fixed 60-second grace period after Continue during which returning to the same target app resumes its continuous-use timer instead of starting another opening-triggered Speedbreaker. A longer absence ends the session, so the next opening starts fresh.
_Avoid_: Bypass, exemption

**Hard lock**:
The first eight seconds of every Speedbreaker, during which only the breathing surface is shown and Home, Back, and Recents do not dismiss it. Calls, emergency or dialer UI, lockscreen, Accessibility settings, and the phone becoming non-interactive always break through immediately.

**Breath**:
The unskippable breathing experience that opens a Speedbreaker. Its total duration is a single global setting between 8 and 60 seconds, default 12; it never varies per app. Leave, Redirect, and Pause become actionable after the first eight seconds; Continue unlocks only when the full breath completes.

**Reflection**:
The fixed prompt “What are you here for?” shown during a Speedbreaker. It requires no input and stores no answer.
_Avoid_: Content feed, recommendation, engagement prompt

**Continue**:
The explicit choice to proceed into the target app after completing a Speedbreaker. It starts the app's continuous-use interval.

**Leave**:
The explicit choice to abandon the target app and return to the Android Home screen.

**Redirect**:
The explicit choice to leave the target app by opening one of exactly four globally configured alternative apps. A target app may replace all four with its own set. Redirect destinations cannot themselves be target apps. Tapping one opens it immediately and ends the target-app session.

**Pause**:
A temporary suspension of enforcement for a single target app, available from the intervention itself. Each target app receives two pause tokens per local day; one token grants 15 wall-clock minutes. Active pauses appear as ongoing, individually cancellable notifications. This token state is the only daily counter; there is no usage total or history.
_Avoid_: Bypass

**Active schedule**:
The days of the week and single daily window during which a target app's policy applies. The optional global schedule has at most one window per day, copyable across days and allowed to cross midnight. A per-app schedule replaces the global schedule. Outside its window the app is unenforced and its session is cleared.
_Avoid_: Calendar, routine

**Choice acknowledgment**:
A brief, non-accumulating affirmation after Leave or Redirect. It carries no points, streaks, dashboard, or moral judgment; the chosen alternative app is the meaningful reward.
_Avoid_: Reward, score, achievement

**Whole-app interception**:
Mediation at the target-app boundary; recognizing a subsection such as Instagram Reels is a later capability.
_Avoid_: Content detection

## Explicitly out of scope

Cumulative usage, daily usage budgets or resets, over-budget state, friction ladders, mercy allowance, Usage Access, and usage counters or history are not part of the product. Time-dependent strictness profiles are a possible later idea, not current scope.
