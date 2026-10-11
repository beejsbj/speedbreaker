# Store distribution status

Updated October 11, 2026. Implementation PR:
https://github.com/beejsbj/speedbreaker/pull/8
Tracking: Linear BJS-532. Working branch: `release/store-distribution`.

| Channel / work | Evidence and status | External dependency |
| --- | --- | --- |
| GitHub stable Android 1.0.1 | **Live:** immutable public release/tag, version code 5. | None for current APK. |
| Candidate Android 1.0.2 | API 36, code 6, separate community and Play builds implemented. Hosted release APKs and Play AAB passed CI 38159600882 at cc5d370; 71 tests passed per flavor, lint 0 errors (community 33 / Play 34 warnings). Final consent/demo integration is in progress; inputs remain unsigned. **Not released.** | Play upload identity/Console signing configuration before a store upload. |
| Optional redirect counts | Owner issue #9 fixed in merged PR #10 at df8808e, then integrated into the store candidate. Zero to four destinations, inheritance and explicit empty overrides supported. Both requested models reviewed; sole wording finding resolved. Main-based CI 38169359412 passed 94 tests and lint 0 errors / 32 warnings. **Not in a published APK yet.** | Integrated API 36 / both-flavor CI and changed-candidate device verification remain. |
| Public privacy/help | **Live:** GitHub Pages deployment run 38160624870 passed, public privacy/help URLs HTTP 200 verified. | Public Play support email and external provider/payment facts pending. |
| Original 1.0.1 reproduction | **Verified:** clean hosted rebuild and signature-copy run 38157458089 passed. Source tree matches canonical release tag. **Not an F-Droid buildserver verification.** | Complete F-Droid pipeline verification still required. |
| F-Droid | Metadata lint/read/rewrite validated. **Blocked; not submitted.** | fdroidserver 2.4.5 rejects valid legacy/release v2/v3 rotation. Owner chose Android 8 retained and wait for support; GitLab account also absent. |
| Google Play | API 36 build, external direct-tip preparation, listing/declaration drafts and CI implemented. **Not submitted; no app draft.** Authenticated Mac Chrome Console inspected October 11: personal account, identity documents uploaded, Google verification pending. **Create app disabled** until account verification completes. | Google's identity approval, followed by owner phone verification. Public email, signing setup, external-tip destination/fee eligibility, legal facts, internal installation tests and production testing remain. |
| Native 16 KB / policy demo | Native compatibility-off x86_64 test, JNI conversion, app launch and crash checks **passed** in isolated hosted run 38163846970; integrated root run 38164514288 also passed at eb14946. Independent review found a failed-transfer acceptance gap; fixed in 45e09ac, independently verified by both models; integrated native run 38165877481 passed. ARM64 runtime untested; literal RELRO formula warnings retained. Fresh consent/Settings recording remains **unverified**. | No account dependency. The demo observer is being fixed to keep the service and synthetic target alive while inspecting UI. |
| iOS feasibility | Native SwiftUI/Screen Time source retained on separate `release/ios-feasibility` branch; all four targets compiled unsigned in hosted run 38157800786. No opening/breathing-overlay parity claim. | **App Store path discontinued at owner's request October 11.** No Apple membership/entitlement/submission pursued. Real-device behavior unverified. |
| Independent code review | Astra high reviewed cc5d370; no new substantive source finding, stale status wording corrected. Actual Sol6.1 xhigh review identified an editable signing-receipt gap; fixed in b899ff0 and independently reproduced positive/tampered host checks. Astra and Sol 6.1 reviewed native integration at eb14946. Sol found a failed-ADB-transfer acceptance gap; resolved in 45e09ac, verified by both models. Final device-tooling review remains pending. | None; receipt and reconciled findings will be recorded here. |

## Owner decisions carried forward

- Preserve Android 8 on F-Droid; wait for rotation support instead of changing
  minimum SDK or signing identity.
- GitHub/community BMC link remains. The owner asked for Play direct tips and
  was unsure after native fees/SDK processing were explained. The recommendation
  is an external direct-tip route, with no Billing/diagnostics SDK. Its link is
  hidden until actual destination, no-reward behavior and policy eligibility are
  verified. Ko-fi Free's zero platform commission does not resolve Google's
  processor-fee interpretation.
- Apple Developer/App Store work stopped; keep the prototype as recoverable work.
- A dedicated public support mailbox is being chosen; no invented email/contact.
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
