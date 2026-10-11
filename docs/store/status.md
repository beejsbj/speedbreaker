# Store distribution status

Updated October 11, 2026. Implementation PR:
https://github.com/beejsbj/speedbreaker/pull/8
Tracking: Linear BJS-532. Working branch: `release/store-distribution`.

| Channel / work | Evidence and status | External dependency |
| --- | --- | --- |
| GitHub stable Android 1.0.1 | **Live:** immutable public release/tag, version code 5. | None for current APK. |
| Candidate Android 1.0.2 | API 36, code 6, separate community and Play builds implemented. Full final CI/signing/release still in progress. **Not released.** | Play upload identity/Console signing configuration before a store upload. |
| Public privacy/help | **Live:** GitHub Pages deployment run 38157020798 passed, public privacy/help URLs HTTP 200 verified. | Public Play support email and account-level financial retention facts pending. |
| Original 1.0.1 reproduction | **Verified:** clean hosted rebuild and signature-copy run 38157458089 passed. Source tree matches canonical release tag. **Not an F-Droid buildserver verification.** | Complete F-Droid pipeline verification still required. |
| F-Droid | Metadata lint/read/rewrite validated. **Blocked; not submitted.** | fdroidserver 2.4.5 rejects valid legacy/release v2/v3 rotation. Owner chose Android 8 retained and wait for support; GitLab account also absent. |
| Google Play | API 36 build, native optional tips, listing/declaration drafts and CI implemented. **Not submitted.** | Developer account/US$25 fee, identity, public email, signing setup, merchant/tax facts, products/public licensing key/prices, live license tests and account-specific production testing. |
| iOS feasibility | Native SwiftUI/Screen Time source retained on separate `release/ios-feasibility` branch; all four targets compiled unsigned in hosted run 38157800786. No opening/breathing-overlay parity claim. | **App Store path discontinued at owner's request October 11.** No Apple membership/entitlement/submission pursued. Real-device behavior unverified. |
| Independent code review | Fresh Astra high and Sol xhigh reviews of fixed c5b0fa2 dispatched against PR8. Results pending. | None; receipt and reconciled findings will be recorded here. |

## Owner decisions carried forward

- Preserve Android 8 on F-Droid; wait for rotation support instead of changing
  minimum SDK or signing identity.
- GitHub/community BMC link remains. Play build implements native optional
  Google Play Billing tips with no rewards or unlocks. Fees still apply; never
  market the native flow as “100% reaches the creator.”
- Apple Developer/App Store work stopped; keep the prototype as recoverable work.
- A dedicated public support mailbox is being chosen; no invented email/contact.
- Heavy Android builds/emulators use hosted CI; full Xcode was absent on the Mac.

No store “submitted,” “approved,” or “live” state is inferred from source files,
a local build, a draft listing, or an unsigned simulator compile.
