# Ready upstream compatibility report

**Draft; not filed.** Destination: existing [fdroidserver #1296 — signing key rotation support](https://gitlab.com/fdroid/fdroidserver/-/issues/1296).
Related [#1342 — rotation/signature copying](https://gitlab.com/fdroid/fdroidserver/-/work_items/1342)
distinguishes signature-block copying from the signer/index/schema support.
Append this concrete reproduction to the relevant existing issue after obtaining
a GitLab account; do not file a duplicate rotation request.

## Title

Valid Android APK signing-key rotation rejected by certificate fingerprint parser

## Reproduction

1. Download the public Apache-2.0 Speedbreaker 1.0.1 APK:
   https://github.com/beejsbj/speedbreaker/releases/download/v1.0.1/Speedbreaker-1.0.1.apk
2. Verify SHA-256:
   `5ca9948dbe730d110f8fd9def95094bb13f9b5ba44c1a51649fc679efb62a966`.
3. `apksigner verify --verbose --print-certs Speedbreaker-1.0.1.apk` succeeds;
   verification restricted to SDK 26–27 and to SDK 28–36 also succeeds.
4. With fdroidserver 2.4.5 / Python 3.11, call:

```python
from fdroidserver import common
print(common.apk_signer_fingerprint('Speedbreaker-1.0.1.apk'))
```

Actual: log reports differing APK signature certificates and the return is None.
`get_first_signer_certificate()` compares the v2 legacy certificate against the
v3 rotated certificate as raw bytes. Public lineage:
https://github.com/beejsbj/speedbreaker/blob/main/signing/speedbreaker.lineage

Expected: accept a cryptographically verified supported rotation, identify the
release signing identity and retain the legacy identity/update relationship for
Android 8. No bypass of signature verification is requested. The existing
package's owners need Android 8 support and cross-channel update compatibility.

Current signature-copy reproduction passed independently on hosted CI:
https://github.com/beejsbj/speedbreaker/actions/runs/38157458089
This does not establish a complete F-Droid buildserver or indexing test.

See [the inclusion record](fdroid.md) for source/tree, certificate fingerprints,
tool versions and the owner decision. All evidence is public app/source data;
no private key or personal device content is part of this report.
