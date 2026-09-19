# Build and verify on bjslab

Use Java 17 and the shared Android SDK. Run these commands sequentially from
the production worktree; never run multiple Gradle processes concurrently.

```sh
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew assembleDebug testDebugUnitTest --no-daemon --no-configuration-cache --max-workers=1
./gradlew lintDebug --no-daemon --no-configuration-cache --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8'
```

Run lint in its own process. Combining compilation, tests, and lint retained
enough compiler classes to exhaust the 512 MiB metaspace budget during the
September 19 validation and contributed to host memory pressure. The separate
lint invocation passed in 2 minutes 20 seconds with a 1 GiB heap.

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Before transferring it,
verify its signature with the SDK's `apksigner verify` and record its SHA-256.
Unit reports are under `app/build/test-results/testDebugUnitTest`; lint reports
are under `app/build/reports/`. Generated APKs and Kotlin caches stay untracked.

Phone validation follows `device-acceptance.md`. A successful build and test
suite do not establish that Samsung's windows, gestures, calls, or background
management behave as expected.
