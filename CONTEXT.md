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

**Redirect**: leave the target for one of one to four configured alternative
apps, or configure none. Redirects must be non-target apps. Per-app alternatives
replace the global set; an empty custom set shows none, while Default inherits.
This follows Burooj’s October 11 [owner correction #9](https://github.com/beejsbj/speedbreaker/issues/9),
which supersedes the earlier exactly-four requirement for the next candidate.

**Pause**: one of two per-target, per-local-day tokens that grants fifteen
minutes immediately. The active pause and today's token count persist locally;
End now on Home or the notification resumes enforcement.

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

The 1.0.0 public-review pass exposed three defects and was published as a testing
prerelease. Version 1.0.1 fixes [call interruption #6](https://github.com/beejsbj/speedbreaker/issues/6),
[settings cancellation #4](https://github.com/beejsbj/speedbreaker/issues/4), and
[pause controls #5](https://github.com/beejsbj/speedbreaker/issues/5). Review and
verification receipts are in [the 1.0.1 record](docs/reviews/2026-10-11-1.0.1.md).
Calls are detected through Android Telecom and communication audio; third-party
calls exposing neither signal are outside that coverage. Phone permission is
required, and missing access suspends breaths.

Burooj requested visible app icons for redirects and a quiet optional support
link in the app. He prefers the Mac for local heavy jobs; use hosted CI or the
Mac for Gradle rather than bjslab. GitHub is the current distribution channel;
F-Droid and Play preparation are described in the
[distribution guide](docs/distribution.md). The separate iOS prototype is
retained; the owner stopped Apple Developer/App Store work.

## Store work — October 11, 2026

Active implementation thread `98543987-c8a8-4578-921d-a593647b609a`, branch
`release/store-distribution`, PR #8, Linear BJS-532. Authoritative evidence:
[docs/store/status.md](docs/store/status.md). Candidate1.0.2/code6/API36 has
community and Play source sets; neither includes a payment SDK. Play prepares an external direct-tip link,
hidden until actual destination/fee eligibility is verified. Native Billing
prototype retained separately on fix/play-tip-concurrency-e0be3c1 after privacy/fee audit.
Stable1.0.1 remains unchanged. Public privacy/support Pages deployed. Historical
APK signature-copy reproduction passed hostedCI38157458089, not F-Droid server.
Owner retains Android8 on F-Droid and waits for valid-key-rotation support; no
signing replacement. Owner abandoned Apple Developer/App Store path; preserve
compiled native prototype without promising iOS parity. Google Play public
mailbox, external-tip destination, signing and test-track prerequisites remain
pending. Enrollment and payment are complete; authenticated Mac Chrome shows
identity verification pending and app creation disabled.

On October 11 the owner supplied a Play Console registration-success screenshot:
developer account created and registration payment completed. This clears
enrollment/payment; it does not establish identity approval, app submission or
agent Console access. Keep the personal receipt email out of public store
contact fields unless the owner explicitly chooses it. Private screenshot is
not a public artifact.
