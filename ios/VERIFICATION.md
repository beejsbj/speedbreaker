# iOS prototype verification

Date: October 11, 2026. Branch: `release/ios-feasibility`, based on `769b403`.
No physical iPhone or developer-account membership has been established.

## Compilation evidence

The initial source commit `c689f577fe653056dab66d741917cb999b0c01b2`
[failed hosted compilation](https://github.com/beejsbj/speedbreaker/actions/runs/38156472181):
`ios/Monitor/ActivityMonitor.swift:12:35: error: cannot find 'Date' in scope`.
The project generated successfully and both shield targets compiled. The fix
adds an explicit Foundation import; the same correction commit also reports
manual stop state accurately in Home.

Corrected source commit: `04434755e635278eabc05762e6727588c11b7dd2`.
[Hosted compile run](https://github.com/beejsbj/speedbreaker/actions/runs/38156660390).
**PASS**, completed October 11, 2026. The log reports `** BUILD SUCCEEDED **`.
Xcode 16.4 (16F6), iOS 18.5 SDK, arm64 generic iOS destination, Debug,
`CODE_SIGNING_ALLOWED=NO`. App plus ActivityMonitor, ShieldConfiguration and
ShieldAction compiled/linked; the log shows all three `.appex` bundles copied
into `Speedbreaker.app/PlugIns` and `ValidateEmbeddedBinary` for each.

Artifact `ios-compile-04434755e635278eabc05762e6727588c11b7dd2` was uploaded
successfully (39,103 bytes, seven-day retention). It contains the generated
project, compile log and xcresult; the run log/link remains the durable receipt.
Warnings: AppIntents metadata extraction skipped because no AppIntents framework
is used; Xcode also warns that all interface orientations should be supported
unless full screen is required. The prototype config is iPhone-only portrait;
orientation/store presentation requirements remain a product readiness item.
No compiler errors remain. This is not a signed/installable artifact.

The subsequent documentation commit changes only Markdown; the passing source
and workflow are exactly those at `0443475`.

Local lightweight checks passed: `bash -n ios/scripts/generate.sh`, parsing all
six configuration plists/manifests with Python plistlib, parsing project and
workflow YAML, `git diff --check`, and release ZIP SHA-256/expected executable
path verification. These do not typecheck Swift; the hosted compiler does.
No Android builds or tests ran, and no Android/parent-document files changed.

Read-only SSH to `macbook` reported `MacBookPro.lan`, user `burooj`, active
developer path `/Library/Developer/CommandLineTools`. `xcodebuild -version`
failed with “requires Xcode”. No keychain was read and no installation or
infrastructure mutation was performed.

## Physical-device checklist

**Every item below is NOT RUN.** Unsigned CI, simulator screenshots and source
inspection cannot substitute for these observations. Record iPhone model, iOS,
Xcode, signing profile/capability state and wall-clock observations, without
publishing private target selections or account details.

1. Fresh install: selection empty, off intent, no monitor registered. Missing
   or mismatched App Group must show an error rather than silently use unrelated
   defaults. Verify all four embedded extensions and signed entitlements.
2. Request individual authorization: cancel/deny, then approve with system
   authentication. Confirm actual status and Settings revocation. Confirm that
   uninstall is not prevented by individual authorization.
3. Open Apple's picker, choose one noncritical app, dismiss, relaunch. Confirm
   counts persist. Exercise category/domain selections separately and observe
   their broader effects. Do not assert excluded safety apps without evidence.
4. Shield now: enter the chosen app, inspect reflection and close-target action.
   Verify system close behavior, no timed lock, no secondary button and no
   launch of Speedbreaker. Return manually and clear; verify the target opens.
   Test Safari/domain behavior separately; don't assume universal browser parity.
5. Register a one-hour daily window starting at the next whole hour. Keep the
   app backgrounded/terminated; use the device after the boundary. Inspect the
   latest callback and actual target shield. At end, verify unshielding and
   observed delivery time. Start registration during a window too.
6. Stop, change selection, re-register, switch daily to immediate, and erase.
   Rapidly repeat around a boundary to look for stale callback reapplication.
   Confirm unrelated Screen Time restrictions are not removed. This is a
   concrete risk: shared defaults and ManagedSettings writes are not atomic.
7. Revoke while shielded and while scheduled, return to Home, inspect recovery,
   and verify access to the target. Reboot, change time zone, and cross DST;
   check for stale intent, callback delays and restriction recovery. Test
   uninstallation/reinstallation and OS backup behavior separately.
8. Confirm navigation, lockscreen, incoming calls and access to safety UI on the
   test device. The app does not observe these events and claims no Android
   safety override; document actual iOS behavior and failure recovery.
9. Erase state: selected tokens and latest callback disappear, shielding off.
   Verify no unexpected logs, usage history or outbound app traffic. Reconcile
   privacy manifests and store privacy answers against the final signed binary.

## Release status

Compilation evidence only. No signed build, device behavior evidence, TestFlight,
App Store approval, Family Controls distribution approval, payment decision or
Android interaction parity. No PR, release, tag or submission created by this
branch. The parent owns integration into its singular distribution PR.
