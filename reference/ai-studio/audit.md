# Recovered prototype audit

## Verdict

The export is a useful visual interaction sketch and a small local-rule CRUD prototype. It is not a functioning app-interception implementation: target detection, usage measurement, enforcement, real redirects, cooldowns, schedules, daily thresholds, the friction ladder, and mercy allowances are absent. Whether to adapt any code or rebuild remains the decision owned by **Choose the Speedbreaker baseline strategy**.

Build status is **not independently verified**. The export lacks a Gradle wrapper; bjslab has Java 17 but no Gradle or Android SDK; and the generated build/test setup contains known stale references.

## What exists

The project is a single Android `:app` module using Kotlin, Jetpack Compose, Material 3, Navigation Compose, an `AndroidViewModel`, StateFlow/coroutines, and Room/KSP.

Four UI surfaces exist:

- A rule-list dashboard with enable, delete, and manual simulation actions.
- An installed-launcher-app picker based on `PackageManager.queryIntentActivities`.
- Per-app configuration for a 5–60 second duration, `STANDARD` or `REELS` category, and one freeform quote.
- A simulated Speedbreaker screen with a timer, breathing animation, delayed quote/counter, and delayed alternatives.

The sole Room entity stores package/name, category, duration, one optional quote, enabled state, and a total simulated-block count. It has no model for the settled domain's opening/usage triggers, active-use accounting, cooldown, active days, optional daily threshold, over-budget state, friction ladder, mercy allowance, alternative apps, or choice acknowledgment.

Relevant source:

- [`AppRule.kt`](mindful-breaker-export/app/src/main/java/com/example/data/AppRule.kt)
- [`BreakerViewModel.kt`](mindful-breaker-export/app/src/main/java/com/example/ui/BreakerViewModel.kt)
- [`RuleConfigurationScreen.kt`](mindful-breaker-export/app/src/main/java/com/example/ui/RuleConfigurationScreen.kt)
- [`SpeedBreakerScreen.kt`](mindful-breaker-export/app/src/main/java/com/example/ui/SpeedBreakerScreen.kt)

## Functional versus simulated

Functional, with caveats:

- Room-backed rule creation, listing, toggling, and deletion.
- Launcher-app discovery, performed synchronously and without a package-visibility `<queries>` declaration.
- Manual countdown and breathing animation.
- Delayed UI content and a simulated-open counter.
- One locally stored quote/reminder per rule.

Simulated or absent:

- [`AndroidManifest.xml`](mindful-breaker-export/app/src/main/AndroidManifest.xml) declares only `MainActivity`; there is no Accessibility service, UsageStats access, overlay/service component, boot receiver, or enforcement mechanism.
- `SIMULATE OPEN` is the only trigger.
- Continue returns to the dashboard instead of launching the target app.
- Journal, Meditate, and Continue Reading are hardcoded labels; all three merely navigate back.
- `REELS WATCHED` displays the simulated Speedbreaker-launch count, passed before its asynchronous increment. It neither detects reels nor resets daily.
- Back is consumed, but Home, Recents, activity recreation, process death, and reboot are not handled.
- The countdown is ephemeral Compose `remember` state rather than a persisted deadline.

## Actual interaction timing

The configured countdown defaults to 10 seconds and permits 5–60 seconds. Independently of that duration:

- Header fade begins at elapsed second 2 and lasts 2 seconds.
- Quote and fake Reels counter fade begins at second 8 and lasts 2 seconds.
- Alternatives fade begins at second 11 and lasts 2 seconds.
- Continue is conditionally inserted when the configured countdown reaches zero.

These fixed thresholds do not implement proportional phases. At five seconds the timer ends before reflection or alternatives appear; at sixty seconds alternatives appear with forty-nine seconds remaining. More seriously, the invisible alternatives remain composed and clickable from the beginning because alpha does not disable hit-testing, and each immediately exits the simulation.

The breathing ring expands for four seconds and contracts for four. Its label logic does not produce a valid inhale / hold / exhale cycle: the initial `Breathe In` is overwritten, leaving only `Exhale` and `Hold...` states.

The quote is either one freeform string or a mandatory hardcoded Thoreau fallback. It is not an optional user-curated library and is not tied to the final third of the configured duration.

## Build, dependency, and test status

- No Gradle wrapper is present.
- The ignored exported `local.properties` points at AI Studio's `/opt/android/sdk`.
- Debug and release configurations reference absent custom keystores; the generated README instructs the user to edit the Gradle file manually.
- Retrofit, Moshi, OkHttp/logging, Firebase BOM, and the secrets plugin are unused. Metadata claims server-side Gemini capability, but no Gemini/API implementation exists.
- No real secret was found; `.env.example` contains only a placeholder.
- The saved screenshot is not recognized as a PNG. The `.png` drawable is actually JPEG data.
- Tests are generated placeholders: `2 + 2`, a nonexistent `Greeting` composable, stale `My Application` text, and stale `com.example` application ID expectations.
- There are no tests for Room behavior, phase timing, lifecycle restoration, triggers, choices, redirects, or enforcement.

One unused repository method collects a Room flow indefinitely while updating the same flow; if called, it could repeatedly trigger its own counter update. It should not be carried forward.

## Ideas worth carrying forward

Carry these as product/design input, not copied implementation:

- Ivory-and-ink visual direction.
- Progressive disclosure: breathing, optional reflection, then explicit choices.
- A restrained breathing-ring motif.
- Local-first, per-target configuration.
- Installed-app selection.
- Room or an equivalent local persistence boundary.

Reimplement rather than copy: phase timing should derive from configured duration, hidden actions should not exist in the interaction tree, breathing should have an explicit cadence, deadlines must survive lifecycle changes, and choices must use the settled Continue / Leave / Redirect semantics.

Do not carry forward as facts or V1 requirements:

- Dashboard counters or gamification.
- Fake Reels detection and the `STANDARD` / `REELS` product boundary.
- Hardcoded Journal, Headspace, and Atomic Habits destinations.
- Mandatory Thoreau fallback.
- `EXIT BLOCKED` framing.
- Fixed 2 / 8 / 11 second phases.
- Back interception presented as system enforcement.
- Unused AI/network/secrets scaffolding, template tests, signing setup, and generic package naming.

