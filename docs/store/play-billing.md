# Optional Google Play tips

Implementation branch: `release/play-tips`, based on parent `release/store-distribution`
at `c8b3b81`. Candidate is 1.0.2 / code 6 / API 36. No publishing or Console
configuration has been performed. All features remain free.

## Parent integration contract

The parent owns `app/build.gradle.kts`, workflows, listing and privacy variants.
This branch does not change them. Add flavors `community` and `play` in dimension
`distribution`, with the same `dev.burooj.speedbreaker` application ID and signing.
Add only `playImplementation("com.android.billingclient:billing:9.1.0")`.
No BuildConfig flag, billing-ktx dependency, application ID suffix, version change,
backend, account system or entitlement is needed.

Both flavors provide `rememberDistributionSupport()` in the presentation package.
Main adds only Home label/summary parameters (existing defaults retained) and uses
that support action. Community retains the BMC browser action and fallback toast;
its source set contains no billing imports. Play has a dismissible Home chooser
and no website/payment-link action or toast fallback. Nothing appears in overlays.

Tests belong in **`app/src/testPlay/java`**, not `src/playTest`. AGP includes this
flavor unit-test source set; shared `testImplementation` dependencies already
include JUnit. Parent hosted CI should run at least:

```sh
./gradlew :app:testCommunityDebugUnitTest :app:testPlayDebugUnitTest :app:assembleCommunityDebug :app:assemblePlayDebug :app:bundlePlayRelease
```

Adjust existing artifact paths/jobs to flavor names. Before parent integration,
main cannot resolve the variant support function because flavors do not yet
exist. No full Android or Compose compile was run here. Verify manifest merging,
AndroidViewModel creation, Compose dialog/lifecycle integration and both flavor
assemblies in hosted CI. No additional test library is required.

## Owner prerequisites — currently blocked

There is no known configured Play developer account, merchant profile, product
catalog or licensing public key. The owner must complete account/identity,
merchant/payment and legal/tax requirements, establish the app entry and testing
track, choose supported regions/prices, and review applicable fee programs.
These are prerequisites, not changes made by this branch.

Create and activate these **one-time consumable** products:

- `speedbreaker_tip_small`
- `speedbreaker_tip_medium`
- `speedbreaker_tip_large`

Configure an ordinary **buy** purchase option for each. Do not configure digital
rewards, rentals, preorders, promotional offers or multi-quantity purchases for
this flow. The client selects a regular eligible buy option and consumes each
verified receipt. Product names in the chooser are small/medium/large; every
price comes from `ProductDetails` and its localized `formattedPrice`, including
the currency, never from fixed amounts or a saved cache. Play's confirmation
screen is authoritative for final payment/tax details.

Copy the app's **RSA licensing public key** from Play Console into
`app/src/play/res/values/billing.xml` as `play_billing_public_key` (base64 X.509,
without PEM wrappers). This is a public verification key, safe to ship; do not
use a service-account credential, API secret or private key. Empty or malformed
configuration disables purchase queries and checkout. No key or products have
been invented. If products/eligible offers are absent, the chooser reports that
tips are unavailable and allows retry; the app remains useful offline.

Google's standard eligible 15% tier applies to the first US$1M in annual revenue;
eligibility/enrollment, other tiers and regional programs affect actual fees.
Do not claim that 100% goes to the creator. See [Play service fees](https://support.google.com/googleplay/android-developer/answer/112622).

The parent must reconcile Play listing/privacy text: Play handles billing data;
Speedbreaker does not collect card details, settings or usage for this flow.
Community's BMC/no-payment-SDK wording does not describe the Play variant.

## Purchase handling and trust boundary

- One activity-scoped BillingClient survives rotation and the Play checkout
  Activity. Resume connects and queries INAPP ownership to recover purchases,
  including transactions completed while Speedbreaker was stopped. Client
  auto-reconnection is enabled; disconnect/errors clear availability. The
  ViewModel closes the connection when its owner is destroyed.
- Queries have 15-second bounds. Transient connection/consume errors trigger
  bounded 2/4/8-second retries; manual Retry and subsequent app resume/restart
  also recover. No background service or always-on network worker is added.
- Choosing a tip takes the checkout lock immediately, queries **fresh** details
  and matches the displayed price/offer token. If those changed, the chooser
  updates and requires another tap. No stale ProductDetails is sent to checkout.
- User cancellation is neutral. Pending is not success and is never consumed.
  Pending, unverifiable and unfinished tips block another payment until recovery
  reconciles ownership, avoiding repeated payment as an error workaround.
- The public-key boundary verifies the exact UTF-8 `Purchase.originalJson`
  signature using RSA/SHA1 (`SHA1withRSA`), then checks purchased state, package,
  exactly one allowed product, nonempty token, quantity one and no suspension.
  Only that path reaches `consumeAsync`.
- Consumption fulfills acknowledgement; no separate acknowledge call is needed.
  Thank-you/success is shown only for an OK consume response with the same token.
  `ITEM_NOT_OWNED`, pending, a launched checkout, or an already-acknowledged field
  is not treated as successful consumption.
- An in-memory token ledger prevents concurrent/duplicate consumption and repeated
  success callbacks during that activity's lifetime. Play's owned-purchase query
  is the durable recovery queue: unconsumed purchases survive process death;
  consumed receipts disappear. No purchase token, signature, receipt JSON,
  personal data or billing debug message is logged or persisted by Speedbreaker.

This is **local signed-receipt verification**, not server verification of current
payment/refund status. It can be bypassed by a modified client, and there is no
cross-device replay database, RTDN handler or refund reconciliation service.
Play enforces ownership/consumption at its API. A stale consumed receipt replay
cannot produce success unless Play returns OK for consumption. If consumption
succeeded but its callback was lost, a later empty query releases the lock without
inventing a success receipt. No content/access/badge is granted, so there is
nothing to revoke on refund. Google recommends server verification for stronger
fraud guarantees; this deliberately has no backend deployment. See
[Google's purchase security guidance](https://developer.android.com/google/play/billing/security).

If Speedbreaker never reopens after an interruption, this client cannot finish
acknowledgement. Play can automatically refund an unacknowledged purchase after
its acknowledgement window (normally three days starting when it becomes
PURCHASED). This client does not promise acknowledgement while it is stopped.

## Verification evidence (October 11, 2026)

Read current official [integration guidance](https://developer.android.com/google/play/billing/integrate),
[9.1 release notes](https://developer.android.com/google/play/billing/release-notes),
[one-time offer guidance](https://developer.android.com/google/play/billing/one-time-product-multi-purchase-options-offers)
and [offer API reference](https://developer.android.com/reference/com/android/billingclient/api/ProductDetails.OneTimePurchaseOfferDetails).

Downloaded the [official 9.1.0 AAR](https://dl.google.com/dl/android/maven2/com/android/billingclient/billing/9.1.0/billing-9.1.0.aar)
and used `javap` on its `classes.jar` to confirm:

- `enablePendingPurchases(PendingPurchasesParams)` and `enableAutoServiceReconnection()`.
- `ProductDetailsResponseListener.onProductDetailsResponse(BillingResult, QueryProductDetailsResult)`;
  the result has `getProductDetailsList()` and `getUnfetchedProductList()`.
- One-time offer list, offer token, formatted price, rental/preorder fields, and
  `BillingFlowParams.ProductDetailsParams.Builder.setOfferToken(String)`.
- `Purchase` state, products, original JSON, signature, package, quantity and
  suspension getters; query, consume, launch and end-connection APIs.

AAR SHA-256: `d28286d4e4c18725510980de01211d9b72501e7146d96817fa0c9999af9b0d22`.
Its manifest declares `com.android.vending.BILLING`, the Play billing service
queries, library-version metadata and non-exported proxy activities. It does
not add INTERNET permission. Parent should inspect the final merged manifest.

**Actually ran:** 13 JUnit tests for the pure ledger, receipt decisions and RSA
verification, compiled with cached Kotlin 2.3.20/JUnit 4.13.2 using direct JVM
tools (256 MB compiler / 128 MB runner); all passed. No Gradle command, full app
build, Android UI test, Play checkout, hosted CI or device test was run here.
`git diff --check` passed. Pure tests cover pending never entering verification/
consumption, cancel, duplicate taps and consume callbacks, query/consume races,
failed-consume recovery, process-restart recovery, multiple receipts, invalid
package/product/token/quantity/suspension, malformed keys and tampered signatures.
They do not exercise the live BillingClient, Android callbacks or Compose UI.

## Required Console/license-test checks — not yet performed

After the prerequisites and parent CI, use an installed Play test-track build
with configured license testers and record evidence for:

1. Empty/invalid public key and missing/inactive products: no checkout, no fake
   prices, retry state, and every free app feature still usable.
2. Each real configured product: regional formatted price matches Play, rapid
   taps start one checkout, user cancellation produces no thank-you.
3. License-test successful payment: signature verifies; exactly one consume
   succeeds; repurchase becomes possible only after processing. Verify
   acknowledgement/consumption in Console, not just the thank-you text.
4. Pending payment completing and canceling, including app restart: no pending
   success/consumption, completed receipts recovered and canceled pending cleared.
5. Network loss and process death between PURCHASED and consume, and around the
   consume callback: recover through query, retry handling, no duplicate charge
   flow, and no invented success when a callback is lost.
6. Rotation, background/foreground, Play Store absent/unavailable, connection
   interruption and offline cold start: dismissible chooser and normal Home,
   no BMC action in Play; community still opens BMC and has no billing dependency.
7. License-test refund and unacknowledged automatic-refund behavior, including
   refund after consume: all app features remain free; no entitlement persists.
8. Inspect logs/artifacts for receipt/token/personal-data leakage and review final
   variant privacy/store declarations.

These transaction/consume/refund checks remain **blocked on Console setup**.
