# Production implementation review and build receipt

Source: `eea5caa63963e4b5f6bb2a3a0a5ac8def7e5e17e`, branch
`work/production-v1`, September 19, 2026. Review baseline: scaffold `49fdafc`.
Authority: BJS-345 architecture and BJS-346 interaction contract. Burooj
explicitly authorized this dedicated session to orchestrate implementation and
physical acceptance. Build commands are in `../build-and-test.md`.

## Independent review

The coordinator reviewed the engine, repository, UI callback paths, manifest,
and Android integration. Separate agents reviewed scaffold standards/spec and
production integration: `branch_history` reviewed runtime behavior and event
ordering; `enforcement_engine` independently reviewed the settings worker's UI
and persistence. The implementation workers had separate bounded scopes.

Material findings resolved before this receipt:

| Finding | Resolution |
| --- | --- |
| Stale full settings snapshots could lose rapid edits | UI transforms latest settings/policy/schedule state under the repository mutex (`7b0e96e`, `ea0b756`). |
| Malformed nested schedules/pauses could load as apparently active | Strict JSON fields, types, ranges and canonical day keys; failure keeps enforcement inactive (`ea0b756`). |
| Incomplete redirect selection could never reach four choices | Local dialog draft with explicit Apply/Clear/Cancel (`5764f2b`). |
| Full-day per-app override incorrectly inherited global | Explicit 00:00–24:00 schedule (`9df2a16`). |
| Missing redirect, notification unavailability and stale overlay error | Home fallback, notification-gated Pause, durable notification publication, successful retry clears error (`1874204`, `7130ccd`). |
| End now could be lost on disconnect or replay stale state | Atomic repository action with ordered, rebased service writes (`792ed78`). |
| End now could reset an unrelated target's session | Match the specific pause generation and apply the event to the existing engine (`3f82267`, `3ef39ce`); reviewer accepted both callback orderings and rapid grant/end coalescing. |
| Queued multiwindow mediation could immediately defeat navigation yield | Yield before presenting another queued target (`214ce21`). |
| A long overlay could erase another visible target's timer | Freeze visible sessions under the overlay; use actual absence for session expiry (`eea5caa`). |

The coordinator accepts the pure engine's BJS-438 implementation and test
criteria. Static code review findings in settings and Android integration are
resolved; BJS-439 and BJS-133 still require their physical UI/controlled-flow
acceptance. The production app is not yet accepted for daily use.

## Final build verification

| Check | Result |
| --- | --- |
| `assembleDebug testDebugUnitTest`, one worker | Pass, 54 seconds |
| Engine tests | 24 passed; zero failures/errors |
| Persistence codec tests | 6 passed; zero failures/errors |
| Separate `lintDebug`, one worker, 1 GiB heap/512 MiB metaspace | Pass, 1 minute 55 seconds |
| Lint findings | Zero errors; 24 warnings for SDK/dependency age, version-catalog use, icon monochrome variants and an unnecessary resource qualifier |
| APK signature | Verifies, one signer, APK Signature Scheme v2 |
| Package | `dev.burooj.speedbreaker`, version `0.1.0` (1), min SDK 26, target/compile SDK 35 |
| APK size | 11,895,976 bytes |
| APK SHA-256 | `39a277b51fbcfe486c6489d24696d51d7119c067257135b76f32297e45f08fc8` |

The immutable transfer artifact is
`/mnt/server-ssd/BJsWorkspace/cockpit/chats/01a0bb73-e76e-7951-9e52-4b3dde620b0f/artifacts/speedbreaker-0.1.0-eea5caa.apk`.
Raw logs in that session directory: `build-3.log` and `lint-2.log`.
JUnit XML and lint HTML/SARIF remain in the normal Gradle report directories.

The first combined compile/test/lint invocation passed assembly and its 28
then-current tests, but exhausted metaspace during lint and caused substantial
shared-host memory pressure. The coordinator terminated only that build's
daemon. Separate runs above completed successfully; do not count the aborted
combined invocation as a passing validation run.

The manifest also explicitly excludes local state from Android cloud backup
and device transfer (`c57d3f2`). Android documents that `allowBackup=false`
alone can leave device transfer enabled on some manufacturers' Android 12+
devices; the explicit exclusion rules implement the local-only contract.
See [Android backup documentation](https://developer.android.com/identity/data/autobackup).

## Device evidence and remaining acceptance

Authenticated phone MCP access worked earlier in the session. Calculator was
resolved as `com.sec.android.app.popupcalculator`. The private APK URL opened
in Chrome, but no successful installation or production app launch was
observed. The phone went offline in Tailscale, last seen at 18:00 EDT on
September 19, and subsequent MCP calls timed out. No production Accessibility
service or selected-app policy was enabled during this session.

The temporary Tailscale-only transfer server is stopped at handoff. Restart a
scoped transfer from the preserved artifact after the phone reconnects; never
weaken MCP's download policy. All controlled checks in `../device-acceptance.md`
remain to be run on this production APK, including real gestures, safety
escape, normal incoming call, reboot/recovery, Samsung idle and layout. Then
configure Burooj's chosen apps and record a full-day trial and actual verdict.
The old probe and these unit tests do not supply those results.

The original `main` checkout and its untracked files were preserved. Source is
committed in the isolated production worktree; no Git remote is configured.
