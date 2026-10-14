# Discovery and store distribution

Status checked October 11, 2026. GitHub stable **1.0.1** is the public Android
release. Store preparation is implemented in PR #8; no F-Droid or Google Play
submission/approval is claimed. The owner discontinued Apple Developer/App Store
work; the native iOS feasibility prototype remains saved.

See [the current evidence and blockers](store/status.md),
[Google Play submission packet](store/google-play.md),
[F-Droid inclusion record](store/fdroid.md), and [retained native iOS prototype](https://github.com/beejsbj/speedbreaker/tree/release/ios-feasibility/ios).
The deployed [privacy policy](https://beejsbj.github.io/speedbreaker/privacy.html)
and [support page](https://beejsbj.github.io/speedbreaker/support.html) are public.

## Helping people find Speedbreaker

The store equivalent of SEO is usually called app-store optimization (ASO).
Describe the actual problem and behavior in plain language: an **Android app
blocker**, **breathing pause before distracting apps**, **mindful screen time**,
and **local digital wellbeing**. These are proposed descriptions, not measured
search-volume claims. Avoid medical promises and keyword repetition.

The public README, repository description/topics, screenshots, identity assets,
and a direct release download give GitHub visitors a clear path from discovery
to installation. A future lightweight website can add an indexable setup guide,
privacy page and app-specific search descriptions. A favicon is useful there;
GitHub's repository tab icon itself is controlled by GitHub.

Store listings should use the same recognizable icon, benefit-led description,
real interface screenshots, and accurate Accessibility explanation. Google Play
provides a 30-character app name, 80-character short description and 4,000-character
full description; irrelevant/repeated keywords can violate its listing guidance.
[Google's listing guide](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)
explains those fields. Apple offers search keywords and recommends precise,
relevant terms; its [search guidance](https://developer.apple.com/app-store/search/)
also explains product-page discovery. Neither channel guarantees search ranking.

## F-Droid

The project uses Apache-2.0, a FLOSS license. F-Droid also requires reviewable,
buildable source; artwork and dependency licensing still need an inclusion
audit. See its
[inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/).

Audit dependency and artwork rights, build from a clean published tag, and
prepare `fdroiddata` metadata for
`dev.burooj.speedbreaker`. Follow the
[submission guide](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
for the actual build recipe and merge request.

Prefer validating reproducible builds with the developer's existing signing
identity before choosing a distribution signing strategy. F-Droid can publish
developer-signed APKs after verifying that its rebuild matches; the published 1.0.1 APK now passes hosted signature-copy reproduction, but the
F-Droid buildserver has not verified it. Its tested server parser rejects the
valid rotation; the owner chose to keep Android 8 and wait for support. A different signing identity can affect updates
between channels. See [reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/).
Never give a store submission worker access to release keys unnecessarily.

## Google Play

The stable 1.0.1 APK targets API 35; the 1.0.2 candidate targets API 36. Google's current requirement for **new mobile
apps and updates** is API 36 or newer, so a Play candidate needs an SDK upgrade
and Android 16 behavior/device testing. This does not prevent distributing the
current APK on GitHub. Recheck the requirement at submission time:
[target API policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).

Prepare an Android App Bundle, establish the Play App Signing/update strategy,
and complete the listing, privacy policy, Data safety and content-rating forms
from the actual app behavior. Confirm the developer account and identity setup
without inventing account details. Newer personal accounts may need a closed
test with at least 12 continuously opted-in testers for 14 days before applying
for production access; see the [account-specific testing rules](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

Accessibility use requires particular attention. Speedbreaker is a general
attention aid, not a service whose primary purpose is assisting disabilities;
it must not claim the `isAccessibilityTool` exemption. Keep its in-app disclosure
and consent, explain the use in the store listing, and submit the declaration
and demonstration video. Review navigation blocking and Settings escape against
the current policy before submission; approval is not guaranteed by passing
technical tests. See [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

## Apple App Store — discontinued

The owner stopped this distribution path on October 11, 2026. The following is
background for the retained prototype, not an active enrollment commitment.


The separate native SwiftUI/Family Controls/Managed Settings/Device Activity
prototype is retained on `release/ios-feasibility`. Hosted CI compiled its app
and three extensions unsigned. It uses static Screen Time shields, lacks
Android's opening interception and eight-second breathing overlay, and has not
been verified on a real iPhone. It is not an App Store candidate. No membership,
distribution entitlement, TestFlight upload or submission is being pursued.

## Optional developer support

The GitHub APK has a quiet **Buy me a coffee** link on Home to
[the developer's Ko-fi page](https://ko-fi.com/beejsbj). It opens externally,
provides no digital rewards or feature unlocks, and adds no billing SDK or
Internet permission. It is never shown in the breathing overlay.

Recheck each store's payment rules before submitting that store's build:

- F-Droid supports public external donation URLs in its
  [Donate metadata](https://f-droid.org/docs/Build_Metadata_Reference/#Donate).
- The Play candidate prepares an external direct-tip route and includes no
  native Billing SDK. Its support action stays hidden until the actual destination
  and eligibility are verified. Google exempts creator tips only when 100% goes
  to the creator and no digital benefit is provided; the FAQ does not explicitly
  resolve ordinary processor fees.
  Ko-fi Free has no one-time platform fee but still has processor fees; see
  [the direct-tip record](store/play-tips.md) and
  [Google's payment FAQ](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en).
- Apple payment preparation is inactive because the owner stopped that path.

These notes were checked October 11, 2026. They are preparation for submission,
not a claim that any store has approved this payment flow.
