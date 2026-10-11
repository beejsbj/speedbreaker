# Family Controls distribution entitlement request — UNSENT DRAFT

Do not submit until the account holder confirms membership, registered IDs,
product behavior, device evidence and the final app/privacy description. This
prototype has not received distribution entitlement approval.

## Account-holder fields to resolve

- Legal/account-holder identity and Team ID: **unknown; owner to supply**.
- App name: Speedbreaker (iOS behavior still under evaluation).
- App ID: **provisional** `dev.burooj.speedbreaker.ios`.
- Extension App IDs (each needs its own request): **provisional**
  `dev.burooj.speedbreaker.ios.monitor`,
  `dev.burooj.speedbreaker.ios.shield-configuration`,
  `dev.burooj.speedbreaker.ios.shield-action`.
- App Group: **provisional** `group.dev.burooj.speedbreaker.ios`.
- Distribution methods requested: owner to choose; TestFlight/App Store are
  anticipated investigation steps, not currently authorized submissions.
- Privacy-policy URL, contact and demonstration: **owner to supply**; existing
  Android-only policy must not be represented as an iOS policy.

## Proposed rationale (revise to match the approved product)

Speedbreaker is a voluntary attention aid for individual device owners. The
current development prototype requests individual Family Controls authorization
and uses Apple's FamilyActivityPicker to obtain opaque application, category
and web-domain tokens chosen by the user. No selection is prepopulated.

The app applies ManagedSettings shields immediately or during a simple daily
DeviceActivity window. A Device Activity Monitor extension applies/clears the
named settings store at system-delivered schedule callbacks. A Shield
Configuration extension presents a static reflection prompt and a close-target
button. A Shield Action extension promptly returns the documented close
response. The prototype does not observe app launches, install a custom overlay,
lock navigation for eight seconds, launch other apps, or collect Screen Time
usage histories. The user can stop restrictions in the app or revoke Screen
Time access in Settings. Individual authorization does not prevent uninstall.

Selected tokens and the current experiment's configuration are persisted in an
App Group solely for app/monitor coordination. One overwritten callback kind and
timestamp supports local diagnostics. No user data is transmitted to the
developer; no analytics, ads, account, reflection input or cloud-sync feature is
implemented. Final privacy/backup claims must be reconciled with a signed build.

## Submission route and gates

[Apple's entitlement instructions](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)
require the Apple Developer Account Holder to request permission for the app
and each Screen Time extension. Use the
[official request form](https://developer.apple.com/contact/request/family-controls-distribution)
or the account's Capability Requests interface. Approval adds a managed
capability; confirm supported provisioning methods and update profiles for all
four targets. Development capability configuration and unsigned compilation do
not constitute distribution permission or App Review acceptance.

Attach the chosen iOS behavior, real-device demonstration and accurate privacy
policy before submission. Do not promise Android behavior or claim this request
has been sent. No support/payment destination is included pending product and
storefront-policy decisions.
