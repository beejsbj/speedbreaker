# S23+ enforcement probe

**Throwaway prototype.** This app answers one question: can a stock Galaxy S23+
reliably detect a selected visible app through an explicitly enabled
Accessibility service and place a timed accessibility overlay in front of it?

It intentionally uses only Java and Android framework APIs. The AI Studio app is
not a runtime dependency.

## Build

```bash
printf 'sdk.dir=/home/admin/.local/share/android-sdk\n' > local.properties
./gradlew --no-daemon :app:assembleDebug
```

## Install

```bash
/home/admin/.local/share/android-sdk/platform-tools/adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

After launch, select a target, save, and enable **Speedbreaker enforcement
probe** under Android Accessibility settings. Usage Access is exposed for later
reconciliation tests but is not required by the first Accessibility-only pass.
The overlay is the default enforcement route. **Arm one background-Activity
experiment** makes the next entry try an Activity once so Samsung's background
activity-launch behavior can be compared directly.

## What this must reveal

- Whether Accessibility events can launch the full-screen breaker from another app.
- Whether Continue cooldown and repeated entry work without a trigger loop.
- Whether target windows remain detectable in split screen and picture-in-picture.
- Whether screen-off/background time is excluded.
- Whether the enabled service reconnects after reboot.
- Which system navigation escapes Android does not allow a personal app to suppress.
