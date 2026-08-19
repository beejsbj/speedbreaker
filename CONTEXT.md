# Speedbreaker

Speedbreaker is a personal Android attention aid that inserts deliberate friction between the impulse to open a distracting app and the choice to continue using it.

## Language

**Target app**:
An installed app chosen for mediation, with its own settings inherited from global defaults. Speedbreaker, the launcher, Settings, input methods, permission controllers, and System UI cannot be target apps.
_Avoid_: Blocked app, distraction app

**Speedbreaker**:
A configurable timed pause presented before access to a target app, ending in an explicit Continue, Leave, or Redirect choice. Its countdown continues if the user leaves the screen and is restored when they return.
_Avoid_: Intervention, blocker, lockout, interruption

**Opening trigger**:
The condition that starts a Speedbreaker when a target app is newly opened outside its cooldown.
_Avoid_: App block

**Usage trigger**:
The condition that starts a Speedbreaker when continuous active use reaches its configured threshold or, when enabled for that target app, cumulative-daily active use reaches its configured threshold.
_Avoid_: Screen-time limit

**Active use**:
Screen-on time when a target app is foregrounded or visibly present in picture-in-picture or split-screen. Background activity does not count.
_Avoid_: Background time, process runtime

**Cooldown**:
A configured grace period after Continue during which returning to the same target app does not start another opening-triggered intervention.
_Avoid_: Bypass, exemption

**Active schedule**:
The days of the week on which a target app's policy applies.
_Avoid_: Calendar, routine

**Over-budget state**:
The state entered when a target app reaches its optional cumulative-daily threshold. It lasts until local midnight and replaces the ordinary Speedbreaker duration with the target app's friction ladder.
_Avoid_: Ban, hard block

**Friction ladder**:
The escalating minimum Speedbreaker durations used in over-budget state: five, ten, twenty, then forty minutes. It advances only after Continue grants access and resets at local midnight.
_Avoid_: Punishment, penalty

**Mercy allowance**:
An optional two-minute access window available once per rolling hour for an over-budget target app. It cannot accumulate, and using any portion consumes that hour's allowance.
_Avoid_: Emergency bypass, free pass

**Continue**:
The explicit choice to proceed into the target app after completing a Speedbreaker.

**Leave**:
The explicit choice to abandon the target app and return outside it.

**Redirect**:
The explicit choice to leave the target app by opening one of up to three user-configured alternative apps shared across interventions.

**Choice acknowledgment**:
A brief, non-accumulating affirmation after Leave or Redirect. It carries no points, streaks, dashboard, or moral judgment; the chosen alternative app is the meaningful reward.
_Avoid_: Reward, score, achievement

**Whole-app interception**:
Mediation at the target-app boundary; recognizing a subsection such as Instagram Reels is a later capability.
_Avoid_: Content detection
