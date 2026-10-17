# Build and test

The production Android app is in `app/`. Use the Gradle wrapper at the repository
root; `reference/ai-studio/` contains an older prototype, not the production build.

## Prerequisites

- **Git** to clone the repository.
- **Java 17**, with `JAVA_HOME` pointing to your JDK installation.
- **Android SDK Platform 36**, Build Tools **36.0.0**, and Platform Tools.
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
The 1.0.2 candidate compiles against and targets API 36, with a minimum device API of 26
(Android 8.0).

## Debug build, tests, and lint

Run these commands **one at a time** from the repository root. The single-worker
setting limits resource contention. Compilation, tests, and lint are separate
processes because combined runs previously caused substantial memory pressure.

```sh
./gradlew assembleCommunityDebug --no-daemon --no-configuration-cache --max-workers=1
./gradlew testCommunityDebugUnitTest --no-daemon --no-configuration-cache --max-workers=1
./gradlew lintCommunityDebug --no-daemon --no-configuration-cache --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'
```

The project configures a 2 GiB Gradle heap and a separate test JVM with up to
2 GiB. Allow additional memory for the Android toolchain and operating system.
Avoid concurrent Gradle builds on a resource-constrained machine.

| Output | Location |
| --- | --- |
| Debug APK | `app/build/outputs/apk/community/debug/app-community-debug.apk` |
| Unit test results | `app/build/test-results/testCommunityDebugUnitTest/` |
| Test HTML report | `app/build/reports/tests/testCommunityDebugUnitTest/index.html` |
| Lint reports | `app/build/reports/` |

The tests include pure enforcement and persistence tests and Robolectric/Compose
UI checks; this command does not require a physical phone. **The gallery test
renders directly into tracked `docs/design/screenshots/` files.** Review
`git status` after tests and keep only intentional screenshot changes.

For a focused engine check:

```sh
./gradlew testCommunityDebugUnitTest --tests '*EnforcementEngineTest*' \
  --no-daemon --no-configuration-cache --max-workers=1
```

To regenerate the gallery deliberately:

```sh
./gradlew testCommunityDebugUnitTest --tests '*GalleryScreenshotTest*' \
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

## Device diagnostics

When a breath does not appear, or ends unexpectedly, a capture from the phone explains
each decision. With the phone connected over USB debugging, reproduce the problem and run:

```sh
adb logcat -s Speedbreaker:I
```

Every line uses the tag `Speedbreaker` and is logged only when something changes, not on
every observation. Lines carry reason codes, booleans, small integers and elapsed
milliseconds: why a breath started or ended, why a chosen app that came into view got no
breath (`opening skipped reason=IN_SESSION`, `SUPPRESSED_UNTIL_INACTIVE`, `SAFETY` and so
on), call-safety signals when safety held a breath back, service and overlay lifecycle,
and the audio-focus and mute steps. The privacy rule is fixed: the only app package names
are the target apps you chose yourself, and never another app's name, a window title or
anything on screen. The log goes to Android's local log buffer only; Speedbreaker writes
no file and sends nothing. Even so, read a capture before you share it, and trim it to the
lines around the problem.

## Release builds

`./gradlew :app:bundlePlayRelease` produces an **unsigned** Android App Bundle at
`app/build/outputs/bundle/playRelease/app-play-release.aab`. Hosted Android checks retain
the APK and bundle for the exact commit. A bundle must be signed with the upload
identity configured in Play Console before upload; it does not carry the APK's
v3 signing lineage. Store preparation does not mean the candidate is released.

For release validation, run `assembleCommunityRelease` and `lintCommunityRelease` separately,
using the same process and worker flags above, and run `testCommunityDebugUnitTest` in
its own invocation. The release variant excludes Compose debug tooling.

The repository does not contain private release keys. An `assembleGithubRelease`
output alone is not the signed APK distributed to users. Maintainer packaging
and signing are documented in [release.md](release.md); contributors do not need
those credentials to build and test the debug app. Generated APKs, local SDK
configuration, dependency caches, and signing credentials should stay untracked.

## Distribution variants

`community` is the F-Droid build with an external optional support link.
`play` is the Google Play build with a configurable external direct-tip route,
hidden until its destination and fee/policy eligibility are verified. Neither
includes a payment SDK, network code or Internet permission; both compile the
offline stand-in in `app/src/offline/`.

`github` is the APK published on GitHub Releases: the community build plus the
in-app updater in `app/src/github/`. It is the only variant with `INTERNET` and
`REQUEST_INSTALL_PACKAGES`, and it uses the platform's `HttpURLConnection` rather
than a network library. Its own tests live in `app/src/testGithub/` and run with
`testGithubDebugUnitTest`.

All three use the same package and version; APK update identity must remain
compatible. Run `testPlayDebugUnitTest` and `lintPlayRelease` for Play, and the
`Github` equivalents for the GitHub build. The CI matrix builds and verifies each
variant separately; `scripts/store_inventory.py` holds each variant's exact
permission set.
