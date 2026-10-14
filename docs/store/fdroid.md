# F-Droid inclusion

**Blocked; no merge request, submission or listing.** Burooj chose on October 11,
2026 to keep Android 8 support on F-Droid and wait for signing-rotation support.
Do not raise the minimum SDK, replace either signing key, use an F-Droid signing
identity, or require existing users to uninstall.

The proposed entry is
[`distribution/fdroid/dev.burooj.speedbreaker.yml`](../../distribution/fdroid/dev.burooj.speedbreaker.yml).
It describes the public 1.0.1 tag, code 5, minimum SDK 26, and developer-signed
binary. The future community flavor excludes the proprietary Play Billing SDK.
The Donate URL is the owner's Ko-fi page, https://ko-fi.com/beejsbj.

## Verified evidence

- fdroidserver **2.4.5** `rewritemeta`, `lint -f dev.burooj.speedbreaker`, and
  `readmeta` accept the entry in a public fdroiddata checkout with its category
  configuration. Category: System. This is metadata validation, not inclusion.
- Public APK SHA-256:
  `5ca9948dbe730d110f8fd9def95094bb13f9b5ba44c1a51649fc679efb62a966`.
- `apksigner` validates the APK and its public rotation lineage. Android 8 uses
  original certificate `8063da543d9e65ccf8ec4267e0710439f67af51d4990720f0ae36bef236a98ca`;
  Android 9+ uses release certificate
  `fe3fcd3e893fe89d97aba6e25b429e9140455846423c2b42c44bfb4fcc320272`.
- A clean hosted rebuild and actual signature-copy verification **passed** in
  [run 38157458089](https://github.com/beejsbj/speedbreaker/actions/runs/38157458089).
  This is **not** an F-Droid buildserver run.
- fdroidserver 2.4.5 `common.apk_signer_fingerprint()` returns **None** for this
  APK. Its `get_first_signer_certificate()` rejects differing v2/v3 certificate
  bytes without accepting this valid rotation. An allow-list with both public
  certificates does not repair that parser failure.

The tested fdroidserver version is a concrete local compatibility result; the
production server's installed version has not been authenticated or inspected.
Maintainers must confirm when their complete build/index/client pipeline accepts
this lineage before inclusion can proceed. Their open [rotation support issue
#1296](https://gitlab.com/fdroid/fdroidserver/-/issues/1296) addresses the signer
index and AllowedAPKSigningKeys data model; the related [#1342 discussion](https://gitlab.com/fdroid/fdroidserver/-/work_items/1342) separates safe signature copying from missing rotation metadata support.
A ready comment for that existing issue is in [the public reproduction packet](fdroid-rotation-report.md).

## Historical reproduction recipe

Canonical tag commit: `3f90ca53e420fb308dcbe48030566f8824ad1688`. The original
hosted APK embeds build HEAD `b9b9869fae6865af7ad7775e52d0e3d671698448` in AGP's
`META-INF/version-control-info.textproto`. Both public commits have the identical
source tree `7270b75c715947b1ecaf3a9e047ecac52364198f`. Reproducing the original
bytes requires the original build HEAD; the workflow verifies tree equality
before building. Neither historical tag nor APK has been changed.

The original apksigner 35 padding requires ZIP normalization before copying its
signatures. The workflow pins the official F-Droid reproducible-apk-tools fork
at `ca728486d42a79f1c9ec0c6ec755c39f251911a8`, verifies `zipalign.py` SHA-256
`7f8820cbe8049083d9b7a3088a41fda7bd895bb5ad5e6bb78b135a00021773a9`, and runs
`--page-size 16 --pad-like-apksigner --replace`. Python 3.11 and
apksigcopier 1.1.1 then validate the actual copied signature. See
[the copier author's padding report](https://github.com/obfusk/apksigcopier/issues/105).
The normalized rebuild SHA-256 is
`0de9a537a0dcc82f05c874f0fc8243437d236e7b09c82aeb34958cfff031e20d`;
no payload entries differ after excluding only JAR signature metadata.

Future release builds disable embedded Git HEAD and preserve unsigned alignment
when signing. External provenance records the source commit and tree. A future
1.0.2 recipe must use its actual immutable published source and signed APK,
with code greater than 5; a working branch is not a release reference.

## Remaining work after rotation support

1. Confirm upstream server/client support using this exact public APK as a
   regression fixture. Validate both Android 8 and Android 9+ update paths.
2. Review the resolved community dependency and asset license inventory, scanner
   results and available F-Droid toolchain. Do not suppress unknown findings.
3. Complete a clean F-Droid buildserver build, normalization if required, and
   signature-copy comparison; enable the metadata build only after they pass.
4. Submit the inclusion merge request with a GitLab account. No authenticated
   GitLab account is currently available. No additional owner approval is needed
   for this already-authorized submission once the technical and access
   dependencies are met.

Official references checked October 11, 2026:
[Inclusion Policy](https://f-droid.org/en/docs/Inclusion_Policy/),
[Build Metadata](https://f-droid.org/en/docs/Build_Metadata_Reference/),
[Reproducible Builds](https://f-droid.org/en/docs/Reproducible_Builds/),
[submission guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
