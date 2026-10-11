# Google Play submission packet

**Prepared source and drafts; not submitted.** Candidate: 1.0.2 / version code 6,
`dev.burooj.speedbreaker`, minimum API 26, target API 36. Recheck the
[target policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
at submission. Hosted CI produces an unsigned Play Android App Bundle and APK.
Account-dependent fields below are deliberately unresolved, not fabricated.

## Listing

Uploadable English metadata is in `fastlane/metadata/android/en-US/`. It states
actual behavior, Accessibility use, the eight-second navigation hold, Settings
escape, Phone permission and call-detection limits. Suggested category:
**Productivity**. App is free, contains no ads, and offers optional consumable
Google Play tips with no digital benefit. No subscriptions. Public URLs:

- Privacy: https://beejsbj.github.io/speedbreaker/privacy.html
- Website/help: https://beejsbj.github.io/speedbreaker/support.html
- Source and issue tracker: https://github.com/beejsbj/speedbreaker

The policy/help URLs are deployed and HTTP 200 was verified. Home links to the
privacy policy. A public developer/support email is required before submission;
Burooj is choosing a dedicated mailbox. No unverified address is committed.
Store graphics are generated from original assets; screenshots and the
Accessibility video must show this build running, never personal device content.

## App access

No account, login, subscription, paywall or server credentials are required.
All free features are available offline. To review:

1. Open the app. Read the full Accessibility disclosure. Decline closes the app
   without accepting consent; reopening shows it again. Accepting opens Home.
2. Open Accessibility settings through Home and enable **Speedbreaker attention
   pauses**. Grant Phone access through Home; otherwise overlays remain off.
3. Pick a harmless app such as Calculator; no targets or redirects are preselected.
4. Check that the current time falls within your chosen schedule, then open the
   target. Observe the breath and navigation hold. After eight seconds Leave,
   configured redirects and Pause become available; Continue waits for the full
   configured breath. Pause also requires notification access.
5. Android Settings and supported system/call safety surfaces interrupt. Disable
   the service or uninstall through Android Settings to stop it.

Sideload restrictions may require Android's Allow restricted settings option;
that is a test-install limitation, not a consent step to hide in a demo.

## AccessibilityService declaration draft

**Not an accessibility tool:** do not set `isAccessibilityTool=true` or claim a
disability exemption. It is a general attention aid; no medical claims.

**Purpose:** identify the app the user is opening and present a breathing pause
for targets selected by the user. Optional check-ins repeat during continuous
visible use. The user decides whether to continue, leave, pause or open a chosen
alternative. Settings, lock-screen and detected calls yield immediately.

**Data accessed:** visible-window package names and Accessibility event/window
metadata, including relevant non-target and system app identities. No screen
text traversal, typed-input reading, message reading, screenshots or usage log.
Package names and timers are processed locally. Selected targets, schedules and
pause state remain in a local database and are never uploaded.

**Actions:** show an Accessibility overlay and perform the user's navigation
choices. For the first eight seconds it intentionally consumes ordinary Back,
Home and Recents; it releases choices as described above. It does not prevent
Settings access, disablement or uninstall, and is not parental or enterprise
control. Actual native Settings reachability during the hold must be demonstrated;
a shell command launching Settings is insufficient evidence.

**Consent:** separate prominent first-run disclosure explaining what is observed,
why and local processing; accept and decline actions precede service enablement.
Runtime Phone/notification consent is separate. Existing privacy acknowledgment
is not a substitute for the visible disclosure in the new-install video.

**Video:** show first launch, all disclosure text (scroll visibly), decline and
reopen, accept, native service grant, target selection, actual breathing/choices,
and Settings escape/disablement. Hosted emulator automation captures this;
attach the resulting accessible video URL only after reviewing it. No video URL
is currently claimed as submitted. The navigation hold remains a substantive
policy review risk even when device tests pass.

Policy sources:
[declaration/disclosure/video](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en),
[Accessibility API restrictions](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).

## Data safety draft

Based on source and Billing Library integration. Review the final merged manifest,
resolved SDK inventory and actual Console payment configuration before saving.
The [official Data safety guide](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
defines collection as off-device transmission, including SDKs, and distinguishes
payment-provider information from purchase history received by an app.

| Data / form field | Proposed answer and basis |
| --- | --- |
| App identities, settings, app activity, call state | Not collected off-device or shared; local processing only. No usage archive. Phone permission checks an aggregate call boolean, not phone numbers. |
| Card/payment account details | Not received by Speedbreaker; Google collects directly under its payment terms. Do not declare that Speedbreaker collects card numbers. |
| Purchase history | **Collected, optional, app functionality.** The Play client receives a signed transaction/token and returns it to Play for consumption. No receipt backend or persisted local history. This is a cautious draft inference from the implementation, not a saved Console declaration. |
| Purchase-history sharing | User-initiated Google Play checkout/processing; guide's user-initiated transfer exception may apply. Confirm the actual form/provider disclosures before marking shared; never claim no payment processing. |
| Ephemeral processing | Do not claim wholly ephemeral: Google retains transactions even though app memory is transient. |
| Location, contacts, messages, media, health, identifiers/analytics/crash logs | No implementation collecting these off-device. Verify final SDK inventory; permissions alone do not establish collection. |
| Required collection | Tips are optional; all free functions work without payment. No Speedbreaker account creation. |
| Data deletion | Clear app storage/uninstall deletes local settings and pause state. Google transaction records/refunds are managed through Google; legal financial records may have mandatory retention. Public support email/account retention facts remain owner-dependent. |
| Encryption in transit | No app network stack or backend. Confirm Google's provider declaration for the payment flow before selecting a universal form checkbox. |
| Independent security review badge | No: model/code reviews and tests are not a certified MASA review. |

Do not copy the community build's “no payment SDK” statement into Play. The
public privacy policy explains both variants and payment-provider retention.

## Content rating / audience drafts

Questionnaire facts: no user-generated content, chat, social interaction, ads,
location sharing, gambling, loot boxes, violence, sexual content, substances or
medical diagnosis/treatment. Optional real-money tips/in-app purchases exist;
answer monetary-purchase questions honestly. Google/IARC assigns the final
rating; do not invent a rating before the completed questionnaire returns it.

Suggested audience is adults seeking mindful app use. The owner must choose the
actual intended age groups, countries and any legally required publisher/trader
status before saving declarations. Do not assert Families compliance or claim
this is a disability tool. Health/breathing wording makes no medical promises;
complete any Console health-app declaration according to the actual category.

## Signing and tracks

Preserve the installed application's release identity. Do not choose a fresh
Google-generated signing key without checking compatibility: it can break
updates from the existing GitHub APK. A bundle's JAR upload signature is not its
installed APK signature, and an AAB does not carry the APK v3 rotation lineage.

Use [Play App Signing's existing-key route](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en)
only after checking the Console's actual support for the API 26–27 legacy/API
28+ release lineage. Existing keys remain on their established signing host.
Any PEPK export goes encrypted directly to Google through the prescribed flow;
never publish it, a keystore, or a password in CI/artifacts/source. Upload-key
choice and configured app-signing certificate must be recorded and compared with
the public fingerprints before the first distribution. A new app account is not
proof this compatibility is solved.

First use internal testing with license testers for all tip success, pending,
cancel, restart, consume and refund cases in [play-billing.md](play-billing.md).
Then meet the actual account's production-access requirements. New personal
accounts may require **12 opted-in testers for 14 continuous days**;
[official testing rules](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
A CI run does not start this clock or replace real testers.

## Exact access dependencies

Burooj must enroll at https://play.google.com/console/signup (US$25 once), supply
identity/payment facts in Google's UI, and provide authenticated Console access.
No account was found. Then create the app, public email, merchant/tax profile,
licensing public key and tip products/prices; complete declarations and signing
with the actual account. All technical preparation remains authorized; no new
blanket approval gate is imposed. Submission/track/live status must come from
Google Play host evidence and be recorded in [status.md](status.md).
