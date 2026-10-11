# Discovery and future store distribution

Status checked October 11, 2026. GitHub Releases is the current Android download
channel. Version 1.0.1 addresses the incoming-call defect found in the 1.0.0
testing prerelease, with phone and emulator verification. F-Droid, Google Play
and the Apple App Store are future destinations;
no listing or approval is claimed for them.

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
developer-signed APKs after verifying that its rebuild matches; this has not yet
been proven for Speedbreaker. A different signing identity can affect updates
between channels. See [reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/).
Never give a store submission worker access to release keys unnecessarily.

## Google Play

The current APK targets API 35. Google's current requirement for **new mobile
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

## Apple App Store

An APK cannot become an iPhone app through a packaging change. An iOS version
needs a separate implementation and an interaction design tested against
Apple's supported restrictions and timing behavior. Treat parity with Android's
opening interception and eight-second overlay as an open feasibility question.

Investigate Family Controls, Managed Settings and Device Activity; Apple's
[Screen Time overview](https://developer.apple.com/documentation/screentimeapidocumentation)
and [individual authorization introduction](https://developer.apple.com/videos/play/wwdc2022/110336/)
are the starting points. The account holder must request Family Controls
distribution entitlement approval for the app and relevant extensions before
distribution. See [Apple's entitlement instructions](https://developer.apple.com/documentation/FamilyControls/requesting-the-family-controls-entitlement).

The practical order is an iOS feasibility prototype, entitlement request,
real-device testing, then TestFlight and App Store submission. Store accounts,
fees, legal declarations and submission approvals remain owner decisions.

## Optional developer support

The GitHub APK has a quiet **Buy me a coffee** link on Home to
[the developer's page](https://buymeacoffee.com/beejsbj). It opens externally,
provides no digital rewards or feature unlocks, and adds no billing SDK or
Internet permission. It is never shown in the breathing overlay.

Recheck each store's payment rules before submitting that store's build:

- F-Droid supports public external donation URLs in its
  [Donate metadata](https://f-droid.org/docs/Build_Metadata_Reference/#Donate).
- Google exempts direct creator tips from Play Billing when 100% goes to the
  creator and there is no digital benefit. Confirm how the actual Buy Me a
  Coffee flow and fees fit that condition; do not assume that a generic support
  button is automatically exempt. See
  [Google's payment FAQ](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en).
- Apple explicitly supports developer tips through in-app purchases. External
  payment links have storefront-specific rules, including a US exception;
  a worldwide iOS release cannot simply assume the Android support link is
  acceptable everywhere. Choose StoreKit tips or an appropriate regional link
  policy during the iOS implementation. See
  [App Review Guidelines 3.1.1](https://developer.apple.com/app-store/review/guidelines/#in-app-purchase).

These notes were checked October 11, 2026. They are preparation for submission,
not a claim that any store has approved this payment flow.
