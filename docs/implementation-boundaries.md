# Production integration boundaries

The accepted product behavior is in `interaction-contract.md` (BJS-346, approved
2026-08-21). `CONTEXT.md` supplies vocabulary. The contract takes precedence over
older prototype or architecture wording, including data retention: persist only
settings and current-day pause state, never app visibility or usage history.

One `:app` Gradle module, package `dev.burooj.speedbreaker`.

## Model and persistence

`model/Settings.kt` contains shared immutable preferences. `Settings.apps` keys
are selected target package names. Null app schedule inherits global schedule;
null global schedule means always active. Null app redirects inherits global;
configured redirect lists have exactly four distinct non-target packages. Schedule
startMinute is 0..1439 and endMinute is 0..1440; 1440 means 24:00. An explicit
per-app always-active override uses every weekday with TimeWindow(0, 1440), while
null per-app schedule continues to mean inherit global.

`persistence.SpeedbreakerRepository.get(context)` is a process singleton:

- `settings: StateFlow<Settings>`
- `pauses: StateFlow<Map<String, PauseState>>`
- `ready: StateFlow<Boolean>`; false until Room has loaded
- `suspend fun updateSettings(transform: (Settings) -> Settings)`
- `suspend fun savePauses(pauses: Map<String, PauseState>)`
- `suspend fun endPause(packageName: String, nowEpochMs: Long): Boolean`

UI edits transform the latest settings while holding the repository mutex,
including nested policy and schedule edits. Persist before publishing flows.
Reject malformed persisted state instead of silently disabling part of a policy.
Write only changed data. Serialize settings and pause maps to JSON inside Room
rows; Java entity/DAO/database + `annotationProcessor(room-compiler)` avoids
adding a second Kotlin compiler plugin. Database errors must not silently imply
protection is active. Disable Android backup for this local-only state.

## Enforcement

Pure Kotlin (no Android) package `enforcement`. It uses shared model types:

```
Observation(visiblePackages: Set<String>, interactive: Boolean,
    safetyUiVisible: Boolean, navigationAway: Boolean = false,
    elapsedMs: Long, epochMs: Long, zoneId: java.time.ZoneId)
Trigger { OPENING, CONTINUOUS }
Breaker(packageName: String, trigger: Trigger, startedElapsedMs: Long,
    breathSeconds: Int)
Choice: Continue, Leave, Pause, Redirect(packageName: String)
Effect: GoHome, LaunchApp(packageName: String), Acknowledge
EngineUpdate(breaker: Breaker?, pauses: Map<String, PauseState>, effects: List<Effect>)
EnforcementEngine(initialPauses: Map<String, PauseState> = emptyMap())
    .update(settings: Settings, observation: Observation): EngineUpdate
    .choose(choice: Choice, settings: Settings, observation: Observation): EngineUpdate
    .endPause(packageName: String, settings: Settings, observation: Observation): EngineUpdate
```

All types internal. `Breaker` timing is monotonic. No runtime sessions survive
process death; restart safely produces a new opening intervention if needed.
Pauses retain their wall-clock expiry across reboot. Only the Android service
calls an engine, serially on its main thread. Engine choices revalidate time and
visibility; disabled UI alone is not an enforcement boundary.

## Android runtime

`observation.SpeedbreakerService` owns the engine, a ComposeView accessibility
overlay, visibility polling/events, and ongoing pause notifications with End now.
Pause notifications require Android notification access and appear only after
the grant is durable. The notification receiver ends pauses atomically in the
repository; the connected service applies the specific event without resetting
other apps' runtime sessions. Service pause writes and receiver transactions are
ordered, with pending engine changes rebased on the current durable pause map.
`observation.ServiceStatus` exposes `connected: StateFlow<Boolean>` and
`fun isEnabled(context: Context): Boolean` for the settings screen. The service
requires affirmative consent and ready persistence before enforcing.

Android integration owns manifest, accessibility XML, system actions, receiver,
runtime, notification and overlay host. It implements its own overlay composable
under `presentation/BreakerOverlay.kt`; settings UI owns other presentation files.

The settings app filters system safety exclusions independently of runtime checks.
It shows real enabled/connected status, never a simulated protected state.
No Usage Access, separate overlay permission, `isAccessibilityTool`, analytics,
network permission, usage history or cloud path. No emergency call tests.

Builds are serialized by coordinator to protect shared host memory. Workers may
write tests but do not start Gradle until given the build slot.
