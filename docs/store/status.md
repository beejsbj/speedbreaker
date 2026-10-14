# Store distribution status

Updated October 11, 2026. Implementation PR:
https://github.com/beejsbj/speedbreaker/pull/8
Tracking: Linear BJS-532. Working branch: `release/store-distribution`.

| Channel / work | Evidence and status | External dependency |
| --- | --- | --- |
| GitHub stable Android 1.0.1 | **Live:** immutable public release/tag, version code 5. | None for current APK. |
| Candidate Android 1.0.2 | API 36, code 6, separate community and Play builds implemented. Hosted release APKs and Play AAB passed CI 38169807900 at 467d700; 94 tests passed per flavor, lint 0 errors (community 33 / Play 34 warnings). Canonical hosted inputs fetched and provenance verified. Final consent/demo integration is in progress; inputs remain unsigned. **Not released.** | Play upload identity/Console signing configuration before a store upload. |
| Optional redirect counts | Owner issue #9 fixed in merged PR #10 at df8808e, then integrated into the store candidate. Zero to four destinations, inheritance and explicit empty overrides supported. Both requested models reviewed; sole wording finding resolved. Main-based CI 38169359412 passed 94 tests and lint 0 errors / 32 warnings; integrated API 36 CI 38169807900 passed 94 tests per flavor. **Not in a published APK yet.** | Changed-candidate device verification remains. |
| Public privacy/help | **Live:** GitHub Pages deployment run 38160624870 passed, public privacy/help URLs HTTP 200 verified. | Public Play support email and external provider/payment facts pending. |
| Original 1.0.1 reproduction | **Verified:** clean hosted rebuild and signature-copy run 38157458089 passed. Source tree matches canonical release tag. **Not an F-Droid buildserver verification.** | Complete F-Droid pipeline verification still required. |
| F-Droid | Metadata lint/read/rewrite validated. **Blocked; not submitted.** | fdroidserver 2.4.5 rejects valid legacy/release v2/v3 rotation. Owner chose Android 8 retained and wait for support; no authenticated GitLab access established. |
| Google Play | API 36 build, external direct-tip preparation, listing/declaration drafts and CI implemented. **Not submitted; no app draft.** Authenticated Mac Chrome Console inspected October 11: personal account, identity documents uploaded, Google verification pending. **Create app disabled** until account verification completes. | Google's identity approval, followed by owner phone verification. Public email, signing setup, external-tip destination/fee eligibility, legal facts, internal installation tests and production testing remain. |
| Native 16 KB / policy demo | Native compatibility-off x86_64 test, JNI conversion, app launch and crash checks **passed** in integrated root run 38169807955 at 467d700. Independent review found a failed-transfer acceptance gap; fixed in 45e09ac and independently verified by both models. ARM64 runtime untested; literal RELRO formula warnings retained. Fresh consent/Settings recording remains **unverified**. | No account dependency. Persistent UI observer now preserves the service and synthetic target. Complete native Settings reachability during the initial hold, disablement, reviewed video and final screenshot packet remain. |
| iOS feasibility | Native SwiftUI/Screen Time source retained on separate `release/ios-feasibility` branch; all four targets compiled unsigned in hosted run 38157800786. No opening/breathing-overlay parity claim. | **App Store path discontinued at owner's request October 11.** No Apple membership/entitlement/submission pursued. Real-device behavior unverified. |
| Independent code review | Astra high reviewed cc5d370; no new substantive source finding, stale status wording corrected. Actual Sol6.1 xhigh review identified an editable signing-receipt gap; fixed in b899ff0 and independently reproduced positive/tampered host checks. Astra and Sol 6.1 reviewed native integration at eb14946. Sol found a failed-ADB-transfer acceptance gap; resolved in 45e09ac, verified by both models. Final device-tooling review remains pending. | None; receipt and reconciled findings will be recorded here. |

## Owner decisions carried forward

- Preserve Android 8 on F-Droid; wait for rotation support instead of changing
  minimum SDK or signing identity.
- GitHub/community support link points to Ko-fi, replacing Buy Me a Coffee. The owner asked for Play direct tips and
  was unsure after native fees/SDK processing were explained. The recommendation
  is an external direct-tip route, with no Billing/diagnostics SDK. Its link is
  hidden until actual destination, no-reward behavior and policy eligibility are
  verified. Ko-fi Free's zero platform commission does not resolve Google's
  processor-fee interpretation.
- Apple Developer/App Store work stopped; keep the prototype as recoverable work.
- Public Play support email: `burooj.bj+playsupport@gmail.com` (owner, October 12).
- **Google Play paused by the owner on October 12, 2026**: the required 12 closed-test
  testers for 14 days are not available. Play build, listing drafts and declarations
  stay in the repo; the automated Play policy video recorder is no longer pursued.
- Owner supplied a Ko-fi page, https://ko-fi.com/beejsbj. It is the GitHub/community
  Home link and the Play direct-tip destination, which stays hidden until verified.
- Heavy Android builds/emulators use hosted CI; full Xcode was absent on the Mac.

No store “submitted,” “approved,” or “live” state is inferred from source files,
a local build, a draft listing, or an unsigned simulator compile.

Current Android build checks passed at eb14946 in hosted run 38164514369; metadata run 38164514416 passed seven tests. Native transfer hardening at 45e09ac passed Android run 38165877503, native run 38165877481 and metadata run 38165877573 (nine tests). Seventeen mocked transcript probes also passed (one authentic pass, sixteen expected rejections).
Detailed review and verification boundaries are recorded in
[the store review checkpoint](../reviews/2026-10-11-store.md).

The root's subsequent checkpoint at 857f915 passed Android 38166287860,
native 38166287973 and metadata 38166287891. The
[redirect review receipt](../reviews/2026-10-11-flexible-redirects.md) records
main integration and the remaining candidate/device boundary. No new signing
or publication followed that merge.

The subsequent native-route investigation found two input/presentation barriers:
outside-window status-bar touches were consumed by the focusable touch-modal
overlay; after adding NOT_TOUCH_MODAL, API 36 showed a full-display native shade
still covered by the breathing surface. Candidate source now prepares temporary
window detachment while a trusted SYSTEM SystemUI panel owns the display,
retaining the original engine timer and haptic state. This repair and its final
Settings/early-close/navigation checks are **not yet verified**. Android 8–10
uses a bounds fallback because exact touch regions are public only from API 30;
its runtime geometry remains unverified. No minimum SDK or signing change.

The integrated 467d700 checkpoint passed Android
[38169807900](https://github.com/beejsbj/speedbreaker/actions/runs/38169807900),
native [38169807955](https://github.com/beejsbj/speedbreaker/actions/runs/38169807955)
and metadata [38169807913](https://github.com/beejsbj/speedbreaker/actions/runs/38169807913).
Downloaded XML independently confirmed 94 tests per flavor with zero failures,
errors or skips. Final manifests contain no Internet, Billing or network-state
permission. Canonical unsigned input recovery verified source tree, host archive
digests and payload receipts; no key, signing or upload operation followed.
Those inputs remain tied to that exact checkpoint, not later documentation or
tooling commits.
