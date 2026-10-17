# Signed APK releases

Tagging a version publishes it: GitHub Actions builds the community release APK,
signs it, and creates the GitHub release with the
APK, a SHA-256 sidecar and a source receipt. The signing keys never go with the APK.

## The normal flow

1. `python3 scripts/bump_version.py X.Y.Z` sets `versionName`, increments
   `versionCode` by one, and opens `## X.Y.Z — unreleased` in `CHANGELOG.md`.
2. Write the user-facing notes under that heading and replace `unreleased` with the
   date. The release notes are this section plus the install line, source commit
   and SHA-256; publishing refuses an empty or still-unreleased section.
3. Open a PR, let the hosted checks pass, and merge to `main`.
4. Tag the merge commit and push the tag: `git tag -a vX.Y.Z <commit> -m "Speedbreaker X.Y.Z"`
   then `git push origin vX.Y.Z`.
5. The release appears with `Speedbreaker-X.Y.Z.apk`, `.apk.sha256` and `.json`. Then
   add the `Version code: ... source ... APK SHA-256 ...` line to the CHANGELOG
   section in a follow-up docs PR, as for earlier releases.

`.github/workflows/release.yml` fails, before any secret is exposed, unless the tag
is `vX.Y.Z`, equals `v` plus `versionName`, and names a commit that is on `main`. The
unsigned APK passes the same dependency and final-manifest policy as `android.yml`.
Tests are not rerun: a tag is cut from a `main` commit that already passed CI. The
signing job re-checks that the tag still names the built commit and the
artifact is the APK the build produced, re-applies the manifest policy, restores
the keystores to mode-600 files that are shredded in an always-run step, and signs
with `scripts/sign_apk.py`, the same code `package_release.py` uses. It verifies SDK
26–27 and SDK 28–36 and fails unless the signer certificates are
`8063da54…98ca` (26–27) and `fe3fcd3e…0272` (28+). `gh release create` never
overwrites: it fails if a release for the tag exists. The published APK is
downloaded again and its SHA-256 compared with the signed file.

To rehearse without publishing, run the workflow manually with a tag and
`publish` off: it signs, then keeps the files and notes as a 14-day workflow
artifact. The `release` environment accepts only `v*` tags, so dispatch from a tag
ref that contains this workflow, or the owner must temporarily allow a branch.
Artifacts of a public repository are readable by anyone: rehearse only with a tag
whose APK is, or is about to be, public.

## Signing locally

`scripts/package_release.py` remains the offline path and uses the same signing code. It builds the
non-debuggable release variant, signs it, verifies the signature, and emits the APK,
SHA-256 sidecar, and source receipt in `dist/`.

```sh
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
python3 scripts/package_release.py
```

Before any credential is requested it applies the same dependency and final-manifest
policy as CI (`scripts/store_inventory.py`: no INTERNET, network-state, Billing,
payment or diagnostics SDKs, no debuggable APK).

The script prompts for the release-keystore password unless
`SPEEDBREAKER_RELEASE_PASSWORD` is supplied in the process environment. On
bjslab, `--password-secret <record-id>` reads that one record through `bjs-bws`
without logging it. For hosted builds, `--skip-build` requires an explicit unsigned
community APK and the verified CI input receipt. See
[distribution automation](store/automation.md) for artifact recovery and exact
source/hash checks.

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

Since October 17 the same keystores and password are also held as secrets of the
GitHub `release` environment (`SPEEDBREAKER_RELEASE_KEYSTORE_BASE64`,
`SPEEDBREAKER_RELEASE_PASSWORD`, `SPEEDBREAKER_LEGACY_KEYSTORE_BASE64`) so a tag can
be signed without the signing host. The owner chose that; Bitwarden stays the backup
of record and the local flow above keeps working. The environment allows only `v*` tags. On October 17 the owner removed the
required-reviewer gate (his GitHub login is the control he accepts), so a pushed
tag signs and publishes without a click. A ruleset blocks moving or deleting `v*`
tags once pushed. To restore the gate: Settings → Environments → release →
Required reviewers.

Restore those exact encrypted keystores from Bitwarden to private, mode-600
files. Keep their password in the credential store or process memory. A new
key cannot replace these records for an update. Do not commit keystores,
passwords, or raw Bitwarden responses. The signing lineage contains no private
key and is safe to keep in Git.

## Threat notes

- **Account compromise or a stray tag.** Anyone or anything that can push a `v*`
  tag (the owner, a leaked token, an agent using the owner's credentials) can publish a release signed with the real key. The
  second factor and recovery methods on the owner's GitHub account now protect the
  signing key as well as the code.
- **Workflow and script changes run with the key.** A tagged run releases the
  secrets to whatever code it executes: `release.yml`, the
  `scripts/` it calls, and the Gradle build of the tagged commit. Before tagging,
  confirm that changes to `.github/workflows/`,
  `scripts/sign_apk.py`, `scripts/store_inventory.py` and the build files were
  reviewed. A release run you did not start is a reason to investigate.
- **No human pause.** With the reviewer gate removed, nothing stops a pushed tag
  between commit and signed release; the tag ruleset only prevents re-pointing
  an existing release tag.
- **A leaked key is not simply replaced.** Updates must keep the signing lineage, so
  suspected exposure needs a deliberate rotation plan rather than a new key. The
  keystores exist on the runner only between the restore step and the always-run
  cleanup step, and secret values are never printed.

Increment `versionCode` for every distributed update and use a matching
`versionName`. `bump_version.py` does both. Preserve the package ID and signing lineage so Android can
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
