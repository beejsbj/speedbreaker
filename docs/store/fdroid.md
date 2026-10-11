# F-Droid inclusion preparation

Status: preparation only; no F-Droid submission or approval is claimed. Policy,
metadata reference, reproducible-build guidance, and the quick-start guide were
rechecked October 11, 2026:

- [Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/)
- [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/)
- [Reproducible Builds](https://f-droid.org/en/docs/Reproducible_Builds/)
- [Submitting to F-Droid Quick Start Guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)

The proposed fdroiddata entry is
[`distribution/fdroid/dev.burooj.speedbreaker.yml`](../../distribution/fdroid/dev.burooj.speedbreaker.yml).
It identifies only public v1.0.1 (version code 5), built from exact source commit
`3f90ca53e420fb308dcbe48030566f8824ad1688`. Its Gradle recipe requests the
release variant (`gradle: yes`, equivalent to `assembleRelease`); the GitHub
release APK is declared as the developer-signed reference binary and its
SHA-256 signer certificate is allow-listed. The build is intentionally disabled
until a clean F-Droid buildserver build and exact APK match have been verified.
The metadata does not claim reproducibility has passed.

## Distribution and update compatibility

Keep the existing package ID `dev.burooj.speedbreaker`, version code ordering,
and `signing/speedbreaker.lineage`. Do not publish an F-Droid-default-signed
variant and do not generate a replacement key. The public v1.0.1 reference is
`Speedbreaker-1.0.1.apk`, SHA-256
`5ca9948dbe730d110f8fd9def95094bb13f9b5ba44c1a51649fc679efb62a966`, signed
with certificate SHA-256
`fe3fcd3e893fe89d97aba6e25b429e9140455846423c2b42c44bfb4fcc320272`.

The app's public signing lineage rotates the original debug identity on API
26–27 to the release identity on API 28 and later. F-Droid's developer-signed
binary workflow is the route that can preserve cross-channel Android updates:
F-Droid builds the APK, compares it with the upstream release, and publishes the
upstream signature only after a match. With that route, an install from GitHub
can update from F-Droid and vice versa, subject to Android's signing-lineage
verification. F-Droid's own default signing key would not be compatible with
those existing installs. A mere metadata allow-list does not establish a match
or guarantee publication.

The APK was signed with `apksigner` 35.0.0. F-Droid's reproducible-build guide
specifically warns that v2/v3 signature copying depends on byte-identical APK
contents before and after signing and on the signing algorithm used. Different
Android build-tools versions, toolchains, resource processing, ZIP contents,
or signing behavior may therefore prevent a match. A clean, F-Droid-managed
rebuild plus signature-copy/reproducibility verification is still required.
The supplied APK hash, version, package, signer fingerprint, and source commit
are release-record facts; no APK was present in this worktree for independent
inspection in this preparation pass.

## Inclusion and build review

- Source is public and the project license is Apache-2.0. The app manifest
  requests Phone state, notifications, and boot completed; it does not request
  Internet permission. Privacy behavior and Accessibility use are described at
  [the public privacy page](https://github.com/beejsbj/speedbreaker/blob/main/docs/privacy.md).
- The Android app dependencies are AndroidX/Jetpack Compose, Room, Kotlin
  coroutines, and test-only Robolectric/Roborazzi/JUnit artifacts resolved from
  Google Maven/Maven Central. Inclusion still needs a transitive dependency and
  license review against the exact release graph; this note is not that completed
  review.
- The project contains the binary Gradle wrapper JAR at
  `gradle/wrapper/gradle-wrapper.jar`, and its wrapper requests Gradle 9.5.1.
  The proposed recipe uses F-Droid's installed Gradle (`gradle: yes`) rather than
  executing the wrapper or downloading that distribution. The binary wrapper
  JAR remains in upstream source and may be reported by source scanning. Do not
  add a speculative `scanignore`: confirm the current scanner finding and
  whether it is acceptable or remove/replace the wrapper in a separately
  authorized upstream change. No srclib is currently identified. No app build
  flags or `prebuild` steps are added without evidence they are needed.
- The wrapper-system Gradle and installed Gradle may differ. AGP 9.3.0 and
  Kotlin 2.3.20 must be checked against the Gradle/JDK/SDK versions actually
  supplied by F-Droid's current buildserver. The project's wrapper pins Gradle
  9.5.1, but that is not proof that the server's system Gradle is compatible.
  `app/build.gradle.kts` sets compile/target SDK 35 and Java 17; no NDK is
  declared. Resolve this compatibility before enabling the build.
- App runtime artwork uses the existing launcher resources; the new publication
  identity kit is documentation artwork and is not bundled by the app. Its
  original artwork is Apache-2.0; the separate Liberation Serif and Lato font
  software is SIL Open Font License with notices in `docs/brand/fonts/`. Those
  fonts are not runtime app dependencies. Preserve those licenses/notices if
  distributing the identity kit. Confirm the app resources and all transitive
  library notices in the final APK before submission; this branch does not add
  artwork or font exceptions.
- The app uses Android's Accessibility service to identify visible apps and
  mediate navigation. The first eight seconds lock ordinary navigation, while
  Settings and safety UI such as incoming-call UI can interrupt. Phone permission
  is used for Android call-state checks; pauses stay off without that access.
  Settings, selected apps and pause state remain local. Description text does
  not promise medical outcomes or perfect call detection.
- F-Droid metadata exposes the public Donate URL
  `https://buymeacoffee.com/beejsbj`, also linked from the upstream README.
  Confirm the donation destination as publisher before submission as required
  by policy.

## Validation and transition to submission

The YAML file was authored and passed the available write-time syntax check.
`fdroidserver` is not installed in this environment (`fdroid --version` returned
`fdroid: command not found`), and no browser, infrastructure install, Gradle
build, or APK download was performed. Therefore `fdroid lint`, `fdroid
rewritemeta`, an F-Droid build, APK signature-copy verification, and binary
comparison remain unrun. Current F-Droid documentation and real fdroiddata
metadata examples were consulted; `checkmetadata` is not listed as a current
fdroidserver CLI command in its documented command set, so do not assume or
invoke it. Use the installed fdroidserver version's `fdroid --help` and
`fdroid lint --help` before running commands; metadata validation must run in a
real fdroiddata checkout because lint also uses repository configuration such
as allowed Categories.

After the parent integrates this preparation:

1. Obtain/verify the official F-Droid GitLab account and repository permissions
   through the authorized owner. Fork fdroiddata and use a new branch; do not
   put signing keys or credentials in the checkout or metadata.
2. Copy the proposed file to the matching fdroiddata path and validate it with
   that checkout's fdroidserver. First check command/version support, then run
   `fdroid lint dev.burooj.speedbreaker`, `fdroid rewritemeta
   dev.burooj.speedbreaker`, and lint again. Review the rewrite diff. `fdroid
   readmeta` can confirm the file parses. Do not use a nonexistent
   `checkmetadata` command; if a current release adds one, establish it from
   `fdroid --help` before use.
3. Run the source scanner and a clean F-Droid build with the exact buildserver
   toolchain. Resolve wrapper-JAR scanner findings, toolchain compatibility,
   missing dependency/license notices, or build failures without broad
   `scanignore` exceptions. Compare the unsigned rebuilt APK against the public
   signed v1.0.1 reference using F-Droid's developer-signature-copying workflow
   and investigate all differences. Verify package, version, signer, and
   lineage handling. Do not remove `disable` until the maintainers' prescribed
   build and signature match checks pass and the owner approves the result.
4. Keep `AllowedAPKSigningKeys` limited to the verified upstream certificate
   fingerprint(s). The v1.0.1 metadata currently lists the provided release
   signer fingerprint; if signature verification requires the legacy signer in
   the rotation lineage, derive and verify that public certificate fingerprint
   from the release artifact and confirm F-Droid's expected field semantics.
   Never substitute F-Droid's signer or publish a signing key.
5. Only after validation and owner approval, enable the build, update this
   evidence/status, and prepare the fdroiddata merge request. Submission and
   final inclusion decisions belong to the parent/owner and F-Droid maintainers.

Suggested fdroiddata staging commands (run from the fdroiddata checkout after
copying this file into it):

    git switch -c add-speedbreaker
    git add metadata/dev.burooj.speedbreaker.yml
    fdroid lint dev.burooj.speedbreaker
    fdroid rewritemeta dev.burooj.speedbreaker
    git diff --check
    git diff -- metadata/dev.burooj.speedbreaker.yml
    git add metadata/dev.burooj.speedbreaker.yml
    git commit -m "New app: Speedbreaker"
    git push -u origin add-speedbreaker

Suggested merge request title: `New app: Speedbreaker`

Suggested merge request body:

> Adds Speedbreaker (`dev.burooj.speedbreaker`), an Apache-2.0 local Android
> attention aid. The metadata describes its eight-second ordinary-navigation
> lock and safety escape, Phone permission call-state use, and local-only data
> handling. The v1.0.1 build uses upstream source commit
> `3f90ca53e420fb308dcbe48030566f8824ad1688` and declares the developer-signed
> release APK as the binary reference to preserve updates across GitHub and
> F-Droid installs. The build remains disabled pending a clean F-Droid build and
> verified reproducibility/signature match; no F-Droid signature is authorized.
> Please review the buildserver compatibility, wrapper-JAR scan result, complete
> dependency and asset license review, and the public signing lineage before
> enabling it.

The commands/body are a draft only. Account authorization, actual merge request,
source/build verification, owner approval, and final submission are deliberately
not performed by this preparation.
