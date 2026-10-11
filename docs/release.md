# Signed APK releases

`scripts/package_release.py` builds the non-debuggable release variant, signs it,
verifies the signature, and emits the APK, SHA-256 sidecar, and source receipt in
`dist/`. Share the APK; the signing keys never go with it.

```sh
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
python3 scripts/package_release.py
```

The script prompts for the release-keystore password unless
`SPEEDBREAKER_RELEASE_PASSWORD` is supplied in the process environment. On
bjslab, `--password-secret <record-id>` reads that one record through `bjs-bws`
without logging it. `--skip-build` signs an already built release variant.
Use it only after building the current committed source.

## Key custody and updates

The encrypted release keystore is
`~/.local/share/speedbreaker-signing/release.jks`, alias `speedbreaker`.
`SPEEDBREAKER_SIGNING_DIR` can override that directory. Its password and an
encrypted keystore backup live in Bitwarden Secrets Manager's infrastructure
project as `SPEEDBREAKER_RELEASE_KEYSTORE_PASSWORD` and
`SPEEDBREAKER_RELEASE_KEYSTORE_BASE64`.

The original install used the local Android development signing identity.
`signing/speedbreaker.lineage` is its public proof of rotation to the dedicated
release key. Android 9 and newer use the new key; Android 8 uses the original
key for signature compatibility. This follows Android's
[apksigner signing-lineage support](https://developer.android.com/tools/apksigner).
The original keystore is backed up as `SPEEDBREAKER_LEGACY_KEYSTORE_BASE64`;
the default local path is `~/.android/debug.keystore`, alias `androiddebugkey`,
with the standard Android development password. Override its path with
`SPEEDBREAKER_LEGACY_KEYSTORE` when restoring on another machine.

Restore those exact encrypted keystores from Bitwarden to private, mode-600
files. Keep their password in the credential store or process memory. A new
key cannot replace these records for an update. Do not commit keystores,
passwords, or raw Bitwarden responses. The signing lineage contains no private
key and is safe to keep in Git.

Increment `versionCode` for every distributed update and use a matching
`versionName`. Preserve the package ID and signing lineage so Android can
update the app while retaining settings and pause state.

## Validation

Run the unit and UI regression suite before the release build. Run lint in a
separate Gradle process as described in `build-and-test.md`. Verify the
packaged APK on the S23+ through the normal installer, check its exact version,
and exercise a controlled Calculator opening, timed unlock, choices and the
Settings safety escape. Record remaining device-specific checks honestly.

On October 11, Burooj accepted OHO being unavailable during a pause and chose
to configure his own targets. The delivery goal is a shareable release; target
configuration and a personal full-day verdict are subsequent owner use.
