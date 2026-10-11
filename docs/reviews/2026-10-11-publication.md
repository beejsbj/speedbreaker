# October 11 public repository and independent reviews

## Published result

The production source is published at [beejsbj/speedbreaker](https://github.com/beejsbj/speedbreaker).
The exact installed APK is available as [v1.0.0, a testing prerelease](https://github.com/beejsbj/speedbreaker/releases/tag/v1.0.0).
The public download was fetched without authentication and its SHA-256 matched
`d09f0387f8340e9d324416fe20d14aa831060481a6b387080a2675486ddb4796`.
Tag `v1.0.0` resolves to APK source `750957be80d1179d0970cf86a663084a9392f5d6`.
The checksum, provenance JSON and Apache-2.0 license accompany the APK.

The owner delegated license choice with the direction to keep the project open.
Apache-2.0 was selected and GitHub recognizes it. Original identity assets use
the project license; bundled fonts retain their SIL OFL notices. The source tag
predates the licensing/documentation pass, so the release explicitly supplies
the license and links current user documentation on `main`.

## Reviews and release judgment

Both reviewers used fresh contexts and independently inspected baseline
`20d53c3585353b15ea782cc96c0c8e99c02bbf85`. Harness records confirm **Astra high**
and **Sol 6.1 xhigh**, matching the requested models and effort. Each ran the
30 existing pure engine/JSON tests successfully and used bounded JVM probes.
Neither reviewer controlled a phone or ran Gradle. They created these public
issues with reproduction evidence and code permalinks:

| Issue | Finding | Evidence |
| --- | --- | --- |
| [#4](https://github.com/beejsbj/speedbreaker/issues/4), P2 | A committed settings write can miss live-state publication if its caller is canceled, allowing a later edit to restore stale settings. | Reproduced independently by both reviewers using the actual repository and controlled DAO doubles. Filed once by Astra. |
| [#5](https://github.com/beejsbj/speedbreaker/issues/5), P2 | Dismissing an active-pause notification on Android 14+ removes the only End now control. | Astra source review plus Android's documented dismissal behavior; statically corroborated by Sol, not device-tested. |
| [#6](https://github.com/beejsbj/speedbreaker/issues/6), P1 | An incoming call presented through SystemUI lacks an immediate safety-yield path. | Sol reproduced the observation/engine gap with actual baseline classes and API doubles; actual incoming-call presentation remains device-unverified. |

Astra found no critical/high-severity blocker in its investigation. Sol found
#6 and recommended holding broad publication until it is corrected and checked
on a phone. The coordinating review agrees that #6 blocks stable release and
that passing unit/UI tests does not establish the call-safety promise. The
existing APK was published only as a clearly marked testing prerelease to meet
the owner's download/publication request. It is publicly downloadable; the
prerelease flag is a warning and release status, not an access restriction.
README and release notes prominently disclose #6 and link all three findings.
No fixes for these findings are claimed in this artifact. Before stable release,
correct the call observation path, preserve the ordinary navigation hard lock,
and verify a coordinated ordinary incoming call during the first eight seconds.

## Public project preparation

A separate Astra high worker prepared the README, user setup/troubleshooting,
privacy, contributing, security, changelog, portable build guide, and GitHub
issue/PR templates in `docs/public-launch` (`1445df2`). Another Astra high worker
created the [identity kit](../brand/README.md) in `design/public-identity`
(`ba2523f`). Both branches were reviewed and merged; production app inputs remain
unchanged from the APK source.

The kit contains original open-arc marks, logos, light/dark README heroes,
favicons, and 1280 × 640 social images, with reproducible vector sources and
font notices. Final compositions and native-size icons were visually inspected.
The live public README loads its hero and all three UI screenshots successfully.
GitHub description/topics, issue forms and private vulnerability reporting are
configured. The custom social-preview image is committed but **not uploaded to
GitHub's repository setting**: the available browser session is signed out.
The [kit guide](../brand/README.md) identifies the exact upload asset and step.

The [GitHub Android check](https://github.com/beejsbj/speedbreaker/actions/runs/38149352846)
passed `assembleRelease`, `testDebugUnitTest` and `lintRelease` on a clean hosted
runner. These application/build inputs are unchanged by the later documentation
and branding merges. This supplements the earlier 43-test phone-release record;
it does not prove reproducible binary equivalence or fix the review findings.

A bounded scan of 242 unpublished history blobs found no high-confidence private
key, GitHub/provider token, AWS access ID, Google API key or credential URL
patterns before the source push. This was a credential-pattern check, not a
security audit. Historical local paths and operator receipts remain as history.

[Distribution and discovery guidance](../distribution.md) records ASO/SEO ideas
and the distinct F-Droid, Google Play and iOS prerequisites from primary sources.
No store submission, developer enrollment or iOS implementation was performed.
