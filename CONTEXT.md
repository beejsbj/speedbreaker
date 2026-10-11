# Speedbreaker

Speedbreaker is a local Android attention aid: a breathing pause at an app
boundary, with an optional check-in during continuous visible use.

The settled V1 behavior is in [the interaction contract](docs/interaction-contract.md).
The old cumulative daily budgets, friction ladder and mercy allowance were
removed during product shaping and are not part of V1.

## Language

**Target app**: an eligible installed app explicitly chosen for mediation.
Nothing is selected on a fresh install. The launcher, Settings, keyboards,
dialer and safety-critical system apps cannot be targets.

**Speedbreaker**: the breathing pause shown on opening or at a check-in.
The first eight seconds lock ordinary navigation; safety UI always yields.

**Opening**: the pause caused by newly entering a target outside its active
session. Returning within sixty seconds resumes the current check-in interval.

**Checking in**: the pause caused by reaching a target's continuous-use interval.
Active use means screen-on visibility, including split-screen and PiP.
Background time does not count.

**Continue**: enter the target after the full configured breath.

**Leave**: end the target session and return Home after the eight-second lock.

**Redirect**: leave the target for one of exactly four configured alternative
apps. Redirects must be non-target apps. Per-app alternatives replace all four.

**Pause**: one of two per-target, per-local-day tokens that grants fifteen
minutes immediately. The active pause and today's token count persist locally;
a notification's End now action resumes enforcement.

**Active schedule**: the optional hours when a target's policy applies.
A per-app schedule replaces the global schedule. Outside it, enforcement and
the current session stop.

**Reflection**: the fixed prompt “What are you here for?” It accepts no input
and stores no answer.

**Whole-app interception**: mediation at the app boundary. Recognizing an
individual subsection such as Reels is outside V1.

## Delivery

Burooj accepts One Hand Operation+ being unavailable while the overlay is
visible. He configures his own targets and alternatives. The October 11 delivery
is a signed, shareable release; a personal full-day verdict is subsequent use.
Linear is the workflow record; device evidence lives in docs/reviews/.

The subsequent public-review pass published the same APK as a **testing
prerelease**, not stable. Incoming-call handling [GitHub #6](https://github.com/beejsbj/speedbreaker/issues/6)
blocks stable release; [#4](https://github.com/beejsbj/speedbreaker/issues/4) and
[#5](https://github.com/beejsbj/speedbreaker/issues/5) track settings cancellation
and pause notification defects. These GitHub issues own the review follow-up.
See [the publication receipt](docs/reviews/2026-10-11-publication.md) and
[distribution guide](docs/distribution.md) before continuing release work.
