# Emulator incoming-call regression

This opt-in mini APK and host script exercise Speedbreaker's **real Android
Accessibility service, Telecom, SystemUI and input routing**. They use an inert
selected app and `adb emu gsm call`; no external call is placed and no call audio
is transmitted. The fixture has no Internet or call-placement permission.

The fixture is excluded from ordinary builds. It changes no production code or
main-app instrumentation. Native `Instrumentation` lives in the fixture APK and
uses `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`; the default UiAutomation behavior
would disable the very service being tested.

## Run

Use a **disposable, English, unlocked API 35 Google APIs phone emulator**. The
script refuses non-emulator serials/devices and API levels below 33. It clears
both Speedbreaker and fixture data, replaces enabled accessibility services,
and changes notification/ringer/display settings. Do not use a personal AVD.

Build on GitHub CI or another authorized compute worker, not a busy local host:

```sh
./gradlew -PcallFixture=true :app:assembleDebug :call-fixture:assembleDebug --no-daemon --no-configuration-cache --max-workers=2
python3 tools/call-fixture/run.py --serial emulator-5554 --reset-test-packages
```

The app APK must include the #6 Telecom fix and declare `READ_PHONE_STATE`.
The script grants that permission to both APKs before enabling the service.
It does not request notification-listener access. The fixture alone receives
notification-policy access to enter silent mode reproducibly.

The separate `Android device regression` workflow checks out the app candidate
separately from the fixture. No production source is copied into this test branch.
It runs on pushes to the isolated `test/device-regression` branch and supports
manual dispatch with an independent app revision:

```sh
gh workflow run android-device.yml --ref test/device-regression -f app_ref=2b0bbddbd3ce647c67282a6c6680d072421a647f
gh run list --workflow android-device.yml --limit 3
gh run watch <run-id> --exit-status
gh run download <run-id> --dir build/device-evidence
```

GitHub requires a workflow to be registered before manual dispatch. The isolated
branch push trigger permits initial registration without editing the main Android
workflow. On that isolated branch the default candidate is pinned to `2b0bbdd`;
elsewhere an empty `app_ref` tests the workflow's own revision. APK SHA-256 values
and the separate app commit are recorded in `environment.json`.

For a pre-fix negative control, select the old app revision and explicitly set
`-f baseline_without_phone_permission=true`. This allows the old APK's absent
permission but never makes a failed call assertion pass.

`--scenario navigation|ringing|silent` runs a single case; the default runs all
three and returns failure if any case fails. `--app-apk` can select a separately
built debug APK. Use `--baseline-without-phone-permission` only with the pre-fix
APK to demonstrate the regression.

## Assertions and evidence

- Setup warms one untimed overlay and, for call cases, a simulated incoming call
  with SystemUI controls. Cold ART/Compose/dialer startup is outside the measured
  window. The measured case then starts a fresh target session; its eight-second
  deadline is unchanged. Trace rows distinguish `warmup` from `test`.
- Navigation: first prove the overlay exists, inject Home and Recents, sample
  that it remains until at least 7.5 seconds after target launch, then require
  release by ten seconds after the first observed overlay (launch can precede
  creation by seconds on a cold renderer). This is a device-level coarse timing check; exact
  7,999/8,000 ms boundaries belong in deterministic engine tests.
- Ringing and silent: first prove the overlay exists. The host injects a GSM
  call only after the fixture signals readiness. Require independent Telecom
  `isInCall` and telephony `RINGING`, the selected target still visible, SystemUI
  Answer and Decline controls, and overlay removal before 7.75 seconds after
  target launch. Launch precedes overlay creation, so this conservatively proves
  removal inside the eight-second lock.
- Fail if another application window takes over during ringing: a dialer activity
  would exercise a different, already-existing safety path. Missing SystemUI
  controls is a failure, not a skip.
- Keep ringing for 700 ms and reject overlay re-entry. Inject a **coordinate
  touch** on SystemUI Answer and require `OFFHOOK`; an accessibility node click
  could bypass an obscuring overlay and is intentionally not used.
- End the simulated call from the host, require idle, explicitly return to the
  selected target and require a new breaker. The host cancels its synthetic call
  and disables Speedbreaker in cleanup, including assertion failures.

Artifacts include screenshots, timestamped window/call booleans in `trace.jsonl`,
instrumentation results, host injection events, emulator fingerprint, APKs and
system dumps/logcat from the disposable emulator. Timing fields measure from
launch/readiness, not a claimed Binder or call-detection latency. No screenshot
or dump from a personal phone is involved.

An API 35 emulator pass proves this image's SystemUI path. It does not establish
Samsung behavior, every supported Android release, multi-SIM, VoIP outside
Telecom, or a hard real-time response bound. Physical-device validation remains
separate. The silent case checks silent ringer mode; it does not assume that
every emulator build leaves `AudioManager.mode` normal while ringing.
It explicitly permits call notifications and their visual effects in the AVD's
DND policy: otherwise silent mode can hide the heads-up independently of the
overlay. The fixture targets SDK 34 to configure that global test policy on
Android 15; the product APK retains its own target SDK unchanged.

## Database seed

Python's standard-library `sqlite3` creates the Room v1 table defined by
`StateRecord.java`, with the exact JSON fields consumed by `SettingsJson.kt`.
It writes consent, a 30-second breath, the fixture as the sole target, and no
pauses. With the product force-stopped and its data cleared, `adb exec-in run-as`
streams the database into `databases/speedbreaker.db`. There is no dependency on
an emulator `sqlite3` executable, root, a production debug receiver or exported
provider. No Room identity hash is forged: Room validates the existing schema
and writes its own identity. Failure to read the seed prevents the positive
overlay precondition and fails the test.

If persistence schema/JSON changes, update the seed alongside this test. Quick
host checks, requiring neither Gradle nor an emulator:

```sh
python3 -m unittest discover -s tools/call-fixture -p 'test_*.py'
python3 tools/call-fixture/run.py --help
```

References: [emulator telephony commands](https://developer.android.com/studio/run/emulator-console#telephony),
[UiAutomation service preservation](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES),
[Telecom call state](https://developer.android.com/reference/android/telecom/TelecomManager#isInCall()).
