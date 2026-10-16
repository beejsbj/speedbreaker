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
**Productivity**. App is free, contains no ads, with no in-app purchases or subscriptions. An external direct-tip route is
prepared but inactive until destination and policy eligibility are verified. Public URLs:

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
The candidate also checks native SystemUI window type, display and touchable
geometry so the overlay can yield its presentation to a full-display native
panel without restarting the breath. Native reachability remains a device gate.
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

The direct-tip candidate has no payment/analytics SDK, Internet permission or
backend. The [official Data safety guide](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
excludes data processed only on the device; review the final dependency and
manifest inventory before saving the actual form.

| Data / field | Draft answer and evidence |
| --- | --- |
| Data collected off-device | No. App identities, local settings, active timers and aggregate call state stay on the phone. |
| Data shared | No. No data payload is attached to browser links or redirect intents. External websites operate under their own policies. |
| Card/payment information, purchase history | Not accessed by the app. No native billing SDK, receipts, payment status or purchase history. Current Play support action is inactive. |
| Location, contacts, messages, media, health, identifiers/analytics/crash logs | No code or resolved runtime SDK collecting these off-device. |
| Data deletion | Clear app storage/uninstall deletes local settings, pause records and the breathing total (also resettable on Home); there is no Speedbreaker account or cloud record. External website/payment records are separate. |
| Encryption in transit | No app data transmitted; follow the form's no-collection branch rather than imply a backend. |
| Independent security review badge | No: code/model reviews are not a certified MASA review. |

The prior native Billing prototype added Google DataTransport and network
permissions through transitive dependencies; it has been removed from this
candidate. Do not reuse its financial-data declarations or claim an enabled
external tip is approved. See [direct-tip eligibility](play-tips.md).

## Content rating / audience drafts

Questionnaire facts: no user-generated content, chat, social interaction, ads,
location sharing, gambling, loot boxes, violence, sexual content, substances or
medical diagnosis/treatment. The app has no in-app purchase. A future external contribution link must be
identified honestly in any applicable monetary-purchase question. Google/IARC assigns the final
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

### Prepared signing support question

**Draft, not sent.** The current [official signing guide](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en),
checked October 11, defaults new apps to Google-generated signing keys. It offers
an existing-key change before open testing or production. It does not establish
that Speedbreaker's existing Android 8 / Android 9+ signing split can be imported.
Resolve that specific compatibility question before distributing a Play APK:

> Our Apache-2.0 app has already shipped outside Play as
> `dev.burooj.speedbreaker`. Its valid public signing lineage uses the original
> certificate on API 26–27 and the rotated release certificate on API 28+.
> We are creating its first Play listing and need installed users to update
> without uninstalling or losing local settings. Which Console enrollment route
> imports both existing keys and their lineage, preserving those SDK-specific
> installed signatures? Can Play's device-specific APKs preserve that split
> beginning at API 28, rather than a later rotation cutoff? If supported, which
> encrypted key exports and public lineage inputs are required?

Supply the public fingerprints from [release.md](../release.md) and the immutable
1.0.1 APK link, never private keystores. Record the support case and actual Console
configuration when available. No key export, upload-key creation or signing
account mutation has occurred during this preparation.

First use internal testing for installation, consent, Phone permission, target
selection, safety escapes, local retention and update compatibility. Verify any
external tip checkout separately before enabling its link. This newly created
personal account falls under Google's published requirement for **12 opted-in
testers for 14 continuous days** before applying for production access;
[official testing rules](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).
The actual track setup becomes available after account verification and app
creation. A CI run or GitHub APK does not start this clock or replace real testers.

## Exact access dependencies

Burooj supplied a Google Play registration-success screenshot on October 11:
developer account created and registration payment completed. The authenticated
Mac Chrome Console was then inspected directly. It shows a **personal account**,
**identity documents uploaded and verification pending**, and contact phone
verification waiting for identity approval. Its **Create app** control is
disabled with “Complete account verifications to create new apps.” There is no
Speedbreaker app draft, uploaded release or submission in this account yet.
Private account identifiers, contact details and screenshots remain outside
this public repository. The next Console step is Google's identity decision,
then the owner's phone verification. Once app creation is enabled, create the
app, supply the public email and actual legal/audience facts,
and complete declarations and signing with the account. The separate external
tip provider needs its own payment/legal setup and verified public destination;
no Play merchant product catalog or licensing key is required for this build. All technical preparation remains authorized; no new
blanket approval gate is imposed. Submission/track/live status must come from
Google Play host evidence and be recorded in [status.md](status.md).
