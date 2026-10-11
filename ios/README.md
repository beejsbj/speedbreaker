# Speedbreaker: bounded native iOS feasibility prototype

This is an investigation, **not an iOS implementation of the Android V1 contract**.
Parent: distribution thread `98543987-c8a8-4578-921d-a593647b609a`,
`release/store-distribution`, base `769b403`. All changes belong to `ios/**`
and `.github/workflows/ios.yml`; the parent owns integration and its single PR.

## What you can actually try

An iPhone running iOS 16 or later can request **individual** Family Controls
access, present Apple's FamilyActivityPicker, and persist the opaque selection.
Nothing is preselected. Counts are shown, not bundle identifiers or app names.
Selections can include apps, categories and web domains; categories are broad,
and this prototype does not implement Android's safety-app exclusion list.

Choose one disposable test target, then either:

- **Shield selection now**: apply the selection to one named ManagedSettingsStore.
- **Register daily window**: register one repeating DeviceActivity schedule,
  using local calendar whole hours, with start before end (09:00–17:00 default).
  No overnight or per-target schedules. A minimum one-hour window deliberately
  exceeds Apple's fifteen-minute minimum. App setup applies shields immediately
  when within the window; the monitor applies at interval start and clears at end.
- **Stop monitoring and clear shields**: set the local intent to off, stop this
  activity, and clear only this prototype's named store. Other Screen Time
  restrictions may remain. Changing picker selection also stops enforcement.
- **Erase local selection and diagnostic**: additionally reset local choices.

The shield configuration extension returns a static reflection and a single
“Close target” button promptly. The shield action extension immediately returns
`.close` for apps, web domains and categories. That tells **the system** to close
the shielded app/browser; it is not Android Home and does not grant access.
To remove the shield, the person must navigate back to Speedbreaker themselves.
No custom URL schemes, private APIs, overlay interception or app-launch workarounds.

Home shows authorization, policy **intent**, monitor **registration**, errors,
and one overwritten callback timestamp. Those are diagnostic observations,
not proof of current restriction on a target. Edited hours apply only when
Register is tapped again; refreshing/reopening restores the saved hours.
There is no usage threshold event, usage report or usage history.

## Feasibility conclusions and limits

| Android contract concern | Supported experiment / limit |
| --- | --- |
| Individual consent | Supported from iOS 16; user can revoke access in Settings. It does not prevent uninstall or iCloud sign-out. |
| Target discovery | Apple's picker returns opaque tokens; this app cannot enumerate installed bundle IDs to recreate Android discovery/exclusions. Shield display extensions may receive system-provided presentation objects; this prototype does not extract names. |
| Opening intervention | An already configured system shield covers a target. No supported launch/foreground observation API in this implementation and no opening-session event. |
| Eight-second navigation lock and 8–60-second animated breath | Not implemented. Shield configuration is a system-rendered configuration, not an arbitrary SwiftUI overlay. No delay, sleep or timer inside shield callbacks. `.defer` is a response/redraw mechanism, not a guaranteed timed navigation lock. |
| Continue, Leave, four redirects | Only system `.close` is demonstrated. No Continue/unshield transition or arbitrary launch of target/alternatives. |
| Continuous visible use, PiP/split screen, 60-second return | Not implemented or proven by schedule callbacks. DeviceActivity usage thresholds are a separate mechanism, not evidence of Android's continuous-session semantics. |
| Schedule boundary timing | Registration is supported. Start callback occurs when the device is first used in the interval; this is not a precise timer. Revocation, background delivery, time zone/DST changes and reboot need physical testing. |
| Two daily tokens / exact fifteen-minute pause | Absent. A fifteen-minute minimum schedule interval does not prove exact fifteen-minute expiry or immediate intervention. |
| Safety overrides | No observation of calls, dialer, lock screen or system navigation. iOS owns the shield and navigation; Android safety behavior has not been recreated. |

Apple's **current** documentation additionally lists
[`ShieldActionResponse.openParentalControlsApp`](https://developer.apple.com/documentation/managedsettings/shieldactionresponse/openparentalcontrolsapp)
introduced in **iOS 26.5**. It asks the system to open the responsible parental
controls app. This prototype targets iOS 16 and compiles with Xcode 16.4, so it
intentionally does not reference that newer case. Its description does not
establish the individual-authorization product flow or an eight-second lock;
those would need a separate SDK/device experiment and owner decision. Do not
turn this bounded implementation into a claim that Apple never supports opening
a controlling app, or that a shield can never be removed via ManagedSettings.

Cross-process policy is one Codable record in App Group UserDefaults. This is
adequate for an experiment, **not transactional coordination**: callback reads
and settings writes can race a manual stop/re-registration. The off-mode guard
and current-window check reduce stale callbacks but do not prove race freedom.
A callback can also arrive late; Home does not silently claim protection.
Do not ship this scheduling policy without recovery/race/device validation.

## Reproducible generation and unsigned compile

Requires full Xcode **16.4** on a Mac for the pinned CI configuration. No external
Swift package dependencies. XcodeGen **2.46.0** is fetched into `ios/.tools`, with
its release ZIP verified against SHA-256
`4d9e34b62172d645eed6457cac13fc222569974098ef4ee9c3368bedf0196806`.
The script does not install anything globally. Generated projects are ignored;
`project.yml` is the source of truth. Network is needed only for the build tool.

From the repository root on a build Mac:

```sh
export DEVELOPER_DIR=/Applications/Xcode_16.4.app/Contents/Developer
ios/scripts/generate.sh
xcodebuild -project ios/Speedbreaker.xcodeproj -scheme Speedbreaker \
  -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' \
  -derivedDataPath ios/build -resultBundlePath ios/compile.xcresult \
  CODE_SIGNING_ALLOWED=NO build
```

The scheme builds the app and all three embedded extensions. CI runs the same
command on `macos-15`, preserves compiler logs, xcresult and generated project,
and never installs, signs, archives, uploads to TestFlight, or submits to Apple.
A successful unsigned build proves SDK compilation/linking and embedding only.
It is not an installable build and cannot validate entitlement authorization.
See [verification evidence](VERIFICATION.md) for the actual result.

The reachable owner Mac was inspected read-only: `xcode-select -p` returned
`/Library/Developer/CommandLineTools` and `xcodebuild -version` failed because
full Xcode is absent. No keychain access, installation or machine mutation was
performed. Hosted CI is the compilation route.

## Owner prerequisites for a signed device experiment

These are dependencies, not requests fulfilled by this branch:

1. Confirm an available physical iPhone, iOS version, device passcode and
   Apple Developer Program membership/account-holder authority. Neither device
   availability nor membership is known. Obtain full Xcode on an authorized Mac.
2. Choose and register final identifiers. `dev.burooj.speedbreaker.ios`,
   `.monitor`, `.shield-configuration`, `.shield-action` and
   `group.dev.burooj.speedbreaker.ios` are **provisional placeholders**, not
   registered facts. Replace the four bundle IDs in `project.yml` and the group
   in `Shared/PrototypePolicy.swift` and `Configuration/Prototype.entitlements`.
3. Set the actual signing team on every target; enable Family Controls for each
   app/extension App ID, and register/assign the same App Group. The included
   entitlement file grants nothing by itself; all provisioning profiles must
   carry the capabilities. Do not use automatic provisioning flags in CI.
4. Generate the project, open it in Xcode, and sign a development build for the
   device. No signing credentials are required or used by this CI.
5. Execute [the physical-device checklist](VERIFICATION.md#physical-device-checklist).
   If recovery is needed, stop/clear in the app, revoke access in iOS Settings,
   and verify access to the test target. Do not begin with a safety-critical app.
6. Before TestFlight/App Store distribution, obtain Family Controls approval for
   **the app and each Screen Time extension**, then correct profiles, privacy
   declarations, product copy and App Review evidence. See
   [the unsent request draft](ENTITLEMENT-REQUEST.md).

## Prototype privacy and policy

This is an iOS-specific description; the parent Android privacy document is not
an iOS declaration. Selection tokens, requested mode and hours stay in the local
App Group. Only the latest callback kind/time is kept; no chosen identity is
logged, no usage timeline, reflection input, analytics, ads, network SDK or
cloud-sync code exists. The shield extensions do not export presentation data.
Apple manages authorization/Screen Time data and the picker.

App and monitor include a privacy manifest declaring App Group UserDefaults
reason `1C8F.1`, no tracking and no developer-collected data. This is source-level
preparation, not an App Store privacy audit. OS backup handling is not customized;
do not reuse Android's “backup disabled” claim. Erasing local state keeps a
minimal default off-policy record; it clears selected tokens and callback data.
Reinstall/backup restoration and access revocation behavior need device testing.

**No support/payment link** is present: the owner has not chosen an iOS payment
product or storefront policy. StoreKit tips and external links have different
review requirements; Android's Buy Me a Coffee link is not automatically an iOS
permission. No submission, entitlement approval or Android parity is claimed.

## Official source rationale (rechecked October 11, 2026)

Apple's pages were fetched from its official Documentation JSON endpoints
(`https://developer.apple.com/tutorials/data/documentation/<path>.json`) as well
as the WWDC transcript. Direct human-readable sources:

- [Screen Time overview](https://developer.apple.com/documentation/screentimeapidocumentation)
  — supported framework family.
- [WWDC22: What's new in Screen Time API](https://developer.apple.com/videos/play/wwdc2022/110336/)
  — individual authorization, revocation and named stores shared with extensions.
- [FamilyActivityPicker](https://developer.apple.com/documentation/familycontrols/familyactivitypicker)
  — same-device individual selection and opaque values passed to restrictions.
- [ShieldSettings](https://developer.apple.com/documentation/managedsettings/shieldsettings)
  — application, domain and category restrictions used here.
- [ShieldConfigurationDataSource](https://developer.apple.com/documentation/managedsettingsui/shieldconfigurationdatasource)
  and [ShieldConfiguration](https://developer.apple.com/documentation/managedsettingsui/shieldconfiguration)
  — quick configuration return, constrained system-rendered shield appearance.
- [ShieldActionDelegate](https://developer.apple.com/documentation/managedsettings/shieldactiondelegate),
  [close](https://developer.apple.com/documentation/managedsettings/shieldactionresponse/close),
  [defer](https://developer.apple.com/documentation/managedsettings/shieldactionresponse/defer)
  — opaque action tokens and response semantics; no timed breath implied.
- [DeviceActivitySchedule](https://developer.apple.com/documentation/deviceactivity/deviceactivityschedule),
  [startMonitoring](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/startmonitoring(_:during:events:)),
  [intervalTooShort](https://developer.apple.com/documentation/deviceactivity/deviceactivitycenter/monitoringerror/intervaltooshort),
  [intervalDidStart](https://developer.apple.com/documentation/deviceactivity/deviceactivitymonitor/intervaldidstart(for:))
  — calendar schedules, thrown registration failures, minimum length and device-use gating.
- [Configuring Family Controls](https://developer.apple.com/documentation/xcode/configuring-family-controls)
  and [requesting its entitlement](https://developer.apple.com/documentation/familycontrols/requesting-the-family-controls-entitlement)
  — development provisioning and account-holder distribution requests per target.
- [App Group UserDefaults reason codes](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitypereasons)
  — `1C8F.1` allows app/extension access within the same App Group.
- [Required reason API declarations](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api)
  — manifests for each executable that uses covered APIs.
- [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
  — review/payment decisions remain prerequisites, not granted by compilation.

Unsupported Android behaviors above are implementation limits and design
inferences from these API surfaces, not claims of Apple endorsement/rejection
of a future Speedbreaker product.
