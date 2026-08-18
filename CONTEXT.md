# Speedbreaker

Speedbreaker is a personal Android attention aid that inserts deliberate friction between the impulse to open a distracting app and the choice to continue using it.

## Language

**Target app**:
An installed app chosen for mediation, with its own settings inherited from global defaults. A target app is not necessarily forbidden; access is made deliberate.
_Avoid_: Blocked app, distraction app

**Intervention**:
A configurable timed pause presented before access to a target app, ending in an explicit Continue, Leave, or Redirect choice.
_Avoid_: Blocker, lockout, interruption

**Opening trigger**:
The condition that starts an intervention when a target app is newly opened outside its cooldown.
_Avoid_: App block

**Usage trigger**:
The condition that starts an intervention when either continuous or cumulative-daily use of a target app reaches its configured time threshold.
_Avoid_: Screen-time limit

**Cooldown**:
A configured grace period after Continue during which returning to the same target app does not start another opening-triggered intervention.
_Avoid_: Bypass, exemption

**Continue**:
The explicit choice to proceed into the target app after completing an intervention.

**Leave**:
The explicit choice to abandon the target app and return outside it.

**Redirect**:
The explicit choice to leave the target app by opening one of up to three user-configured alternative apps shared across interventions.

**Whole-app interception**:
Mediation at the target-app boundary; recognizing a subsection such as Instagram Reels is a later capability.
_Avoid_: Content detection
