# Build and test

The production Android app is in `app/`. Use the Gradle wrapper at the repository
root; `reference/ai-studio/` contains an older prototype, not the production build.

## Prerequisites

- **Git** to clone the repository.
- **Java 17**, with `JAVA_HOME` pointing to your JDK installation.
- **Android SDK Platform 35**, plus Android SDK Build Tools and Platform Tools.
- The repository's **Gradle wrapper** (`./gradlew`, or `gradlew.bat` on Windows).
- Network access for the first dependency download.

Clone the repository and enter its root:

```sh
git clone https://github.com/beejsbj/speedbreaker.git
cd speedbreaker
```

Set `ANDROID_SDK_ROOT` to your local Android SDK directory, or let Android Studio
write `sdk.dir` into an untracked `local.properties`. Select JDK 17 as the Gradle
JDK when using Android Studio. SDK paths vary by machine; do not commit yours.
The app compiles against and targets API 35, with a minimum device API of 26
(Android 8.0).

## Debug build, tests, and lint

Run these commands **one at a time** from the repository root. The single-worker
setting limits resource contention. Compilation, tests, and lint are separate
processes because combined runs previously caused substantial memory pressure.

```sh
./gradlew assembleDebug --no-daemon --no-configuration-cache --max-workers=1
./gradlew testDebugUnitTest --no-daemon --no-configuration-cache --max-workers=1
./gradlew lintDebug --no-daemon --no-configuration-cache --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'
```

The project configures a 2 GiB Gradle heap and a separate test JVM with up to
2 GiB. Allow additional memory for the Android toolchain and operating system.
Avoid concurrent Gradle builds on a resource-constrained machine.

| Output | Location |
| --- | --- |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Unit test results | `app/build/test-results/testDebugUnitTest/` |
| Test HTML report | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Lint reports | `app/build/reports/` |

The tests include pure enforcement and persistence tests and Robolectric/Compose
UI checks; this command does not require a physical phone. **The gallery test
renders directly into tracked `docs/design/screenshots/` files.** Review
`git status` after tests and keep only intentional screenshot changes.

For a focused engine check:

```sh
./gradlew testDebugUnitTest --tests '*EnforcementEngineTest*' \
  --no-daemon --no-configuration-cache --max-workers=1
```

To regenerate the gallery deliberately:

```sh
./gradlew testDebugUnitTest --tests '*GalleryScreenshotTest*' \
  --no-daemon --no-configuration-cache --max-workers=1
```

The gallery uses sample settings and monogram app icons. See the
[UI notes](design/calm-ui.md#screenshots) for its capture setup.

## Try a development build

Open the debug APK on a test phone or install it through Android Studio. Follow
[Getting started](getting-started.md) to enable Accessibility and choose a
harmless test target, such as Calculator. The package ID is
`dev.burooj.speedbreaker` for both debug and release builds.

Your local debug signing key may differ from the published release's signing
identity. Android may therefore refuse an in-place update between them. Use a
test device or emulator with no existing installation where practical;
uninstalling an existing copy removes its local data.

Automated tests do not verify a manufacturer's navigation, incoming calls,
reboot recovery, or battery management. The [device acceptance matrix](device-acceptance.md)
is a historical test checklist; report which cases you actually checked on your
build. The [October 11 release record](reviews/2026-10-11-release.md) identifies
what was verified on 1.0.0. Older observations are not evidence for a new build.

## Release builds

For release validation, run `assembleRelease` and `lintRelease` separately,
using the same process and worker flags above, and run `testDebugUnitTest` in
its own invocation. The release variant excludes Compose debug tooling.

The repository does not contain private release keys. An `assembleRelease`
output alone is not the signed APK distributed to users. Maintainer packaging
and signing are documented in [release.md](release.md); contributors do not need
those credentials to build and test the debug app. Generated APKs, local SDK
configuration, dependency caches, and signing credentials should stay untracked.
