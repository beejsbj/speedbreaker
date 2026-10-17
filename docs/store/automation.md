# Distribution automation

CI prepares and verifies reviewable inputs. Signing happens in one of two
places: the tag-restricted `release` GitHub environment (a pushed `vX.Y.Z` tag, see
[release.md](../release.md)) or the established private signing host. Store status
still requires actual host evidence.

| Workflow | Trigger / result |
| --- | --- |
| `android.yml` | App PR/main changes or manual dispatch. GitHub/community/Play unsigned APKs, Play AAB, unit/UI tests, release lint, resolved dependency/license inventory, final manifest assertions, exact-source artifact hashes. |
| `release.yml` | Pushed `vX.Y.Z` tag, or manual run for an existing tag. Unsigned GitHub-channel APK (community plus the in-app updater) plus inventory and policy checks in a job with no secrets; then, in the tag-only `release` environment, signing with `scripts/sign_apk.py`, per-SDK signature and certificate checks, and a GitHub release (APK, `.sha256`, `.json`) verified by re-download. Manual runs with `publish` off keep the signed files only as a workflow artifact. |
| `android-device.yml` | Current manual/isolated tooling workflow; PR/main triggers, historical task selection and fresh-install captures are being integrated by the store-demo worker. API 35/36 Telecom/SystemUI/navigation regression is separate from the unverified final consent video. |
| `android-pages.yml` | App/build/native/fixture PR/main changes or manual dispatch. Checks all packaged native ABIs statically, then boots explicit API 36 x86_64 16 KB AVD with compatibility mode off, exercises the actual packaged JNI conversion, verifies native mappings and app launch, and retains failure/success evidence. No ARM64 hardware certification claim. |
| `store-metadata.yml` | Listing/artwork PR/main changes. Character/image limits, deterministic committed-export freshness, generated 512px icon and 1024×500 feature graphic artifacts. |
| `reproducible.yml` | Manual published-APK check. Immutable 1.0.1 clean source rebuild and actual signature-copy verification, with pinned historical padding/tool inputs. |
| `pages.yml` | Public-document changes or dispatch. Render privacy/help and deploy GitHub Pages. |

Actions and build/export tools are pinned, and Dependabot proposes weekly updates
to Gradle dependencies and Actions SHAs. No private signing key, store account
credential, payment information or personal-device evidence is uploaded; the
`release` environment's secrets are available only to its signing job and are
deleted from the runner when it ends. CI's
unsigned artifacts are not installable store releases and do not prove approval.

## Recover verified CI inputs

After the current commit's Android checks finish successfully, on the existing
signing/orchestration host:

```sh
python3 scripts/fetch_store_artifacts.py --run-id RUN_ID
```

The helper requires a successful canonical Android workflow for local HEAD,
checks GitHub's SHA-256 archive digests before extraction, rejects unsafe paths,
checks the exact source tree (including public PR merge checkout), and compares
APK/AAB hashes against the hosted provenance receipt. It downloads into a fresh
`build/store-inputs/RUN_ID/`, including dependency, license and manifest evidence.
Use `--commit FULL_COMMIT` only to inspect another known candidate; the signing
script still requires the current committed source. Failed, stale, missing or
expired inputs are rejected rather than relabelled as the current build.

Do not restore keys in a build job or on a build worker; only the `release` environment's signing job may. To sign locally the GitHub APK, use its
`.apk.input.json` receipt with the established packaging flow:

```sh
python3 scripts/package_release.py --skip-build \
  --unsigned-apk build/store-inputs/RUN_ID/github/apk/app-github-release-unsigned.apk \
  --build-receipt build/store-inputs/RUN_ID/github/apk/app-github-release-unsigned.apk.input.json \
  --output-dir dist/CANDIDATE
```

The signer independently re-downloads the canonical successful Android run's GitHub inventory and APK, verifies GitHub archive digests and hosted provenance, and compares both the actual local APK and the entire receipt with server-derived evidence before reading the release password. Editing a local receipt to match an altered APK cannot bypass this check. It refuses receipt/source/hash mismatches, a non-GitHub input,
debuggable APKs and an existing output, then signs with the original legacy and
release keys plus public rotation lineage. It verifies SDK 26–27 and SDK 28–36,
and emits signed SHA-256 and source/build receipts. Its password is read through
the existing credential flow or prompt; never pass a password in the command.
Heavy compilation occurs on hosted CI or the Mac, not the signing host.

The Play bundle is unsigned. After account setup, configure existing-key Play
App Signing and an upload identity before signing/uploading it. Do not mistake
upload-key certification for installed update compatibility. No automated
production upload is configured while Console identity, API access and signing
configuration are absent. The prepared packet in [google-play.md](google-play.md)
lets authenticated Console work continue without recreating assets/declarations.

## Publishing evidence

Record actual release/track/submission IDs, source and installed certificate,
URLs, timestamps and host states in [status.md](status.md). Never derive
submitted/approved/live from an artifact upload or a green check. F-Droid remains
blocked on rotation support with Android 8 retained. Apple publication was
stopped by the owner; its prototype is retained on `release/ios-feasibility`.
