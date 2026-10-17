# Security

Speedbreaker uses an Accessibility service to observe visible app identities
and temporarily hold a breathing overlay. Unexpected access to private data,
unsafe behavior around system screens, or exposed credentials should be
reported as security concerns. Ordinary setup and UI problems can use the
[bug report form](https://github.com/beejsbj/speedbreaker/issues/new/choose).

## Report a vulnerability

Use GitHub's [private vulnerability reporting](https://github.com/beejsbj/speedbreaker/security/advisories/new)
to contact the maintainer. Do not publish exploit details, credentials, or
personal data in a public issue. No response-time commitment is currently
published.

Include the affected version, phone model and Android version, expected and
actual behavior, impact, and the smallest safe reproduction. Do not test against other people's devices or data.

## Versions and verification

Only the [latest release](https://github.com/beejsbj/speedbreaker/releases/latest)
is supported. No long-term support or security backport policy has been
established. When reporting, give the exact version and whether it came from a
GitHub release or a local build.

Download APKs from this repository's
[Releases page](https://github.com/beejsbj/speedbreaker/releases). Where a
release provides a checksum, compare it with the file you downloaded before
installation; the [README](README.md#verify-the-apk) also lists the signing
certificates to expect. A checksum verifies a file match; it is not a security audit.

Accessibility can be disabled in Android Settings at any time. See
[setup and recovery](docs/getting-started.md#stop-or-remove-speedbreaker) and
[privacy](docs/privacy.md) for the permissions and stored data.
