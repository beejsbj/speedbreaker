# Google Play direct tips

**External direct-tip preparation; no active Play support link or submission.**
Burooj asked for Play's direct-tip route and a lower-fee alternative to BMC.
After explaining native Billing's fees and SDK processing, the owner was unsure;
the implementation recommendation is an external, no-reward direct contribution
with no platform cut. The community/GitHub BMC link remains unchanged.

## Candidate behavior

The Play flavor has **no Billing SDK, billing permission, Internet permission,
purchase receipt processing or diagnostics transport**. Its external support
seam uses `app/src/play/res/values/support.xml`:

- `play_direct_tip_url` is empty.
- `play_direct_tip_verified` is false.
- Until configured and verified, the support action is hidden. The privacy policy
  remains accessible on Home and before consent. All app features remain free.
- Once approved/configured, a valid HTTPS URL opens in the user's browser. No
  app settings, selected packages, activity, identifier or purchase token is
  attached. There is no BMC or native-checkout fallback in Play.

CI verifies the final release manifests and resolved graphs of both flavors;
no Billing/DataTransport dependency is permitted in this direct-tip candidate.
The earlier native implementation and its review fixes are retained separately
on `fix/play-tip-concurrency-e0be3c1`; it is not shipped by this candidate.

## Provider recommendation and unresolved policy detail

[Ko-fi Free](https://help.ko-fi.com/hc/en-us/articles/360002506494-Does-Ko-fi-take-a-fee)
charges **0% platform fee for one-time tips**. New creator accounts default to
Standard mode, which charges 5%; turn it off in Settings → Payment. PayPal/Stripe
processing fees still apply, typically around 3% + a fixed amount. Do not describe
this as zero total fees or promise a specific net receipt before configuring the
actual country/currency/account. Keep memberships, monthly contributions, shop,
commissions, rewards, badges and supporter-only content out of this tip flow.

[Google's direct-tip exception](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en)
requires 100% to reach the creator and no digital benefit. It does **not explicitly
resolve payment-processor deductions**. Zero platform commission alone is not
proof of eligibility. Before enabling the Play link, record the actual checkout,
fee mode, account destination, reward-free behavior and Google's applicability
answer/review evidence. Never assert an exemption has been approved from a fee
page or a local config flag.

If Google rejects that external flow, bring the concrete options back to the
owner: a genuinely qualifying direct transfer (with regional/availability costs),
or native Play Billing with its fees and disclosed SDK processing. Do not
silently add a payment/diagnostics SDK or change the payment route.

## Missing owner/account facts

No verified Ko-fi/Stripe/PayPal tip destination was supplied. The owner must
create/configure the payment profile and supply the public URL, currency and
legal/payment facts in the provider's UI. No password or private key belongs in
chat, source, CI or artifacts. The dedicated public support email is also pending.
The Google Play account is enrolled and paid. Identity verification is pending;
app creation is currently disabled. Actual support-case access and submission
still need the authenticated account's available controls.

## Prepared policy clarification request

This is a draft for the authenticated developer support form, **not sent**:

> Speedbreaker is a free attention aid. All features are free, with no ads,
> subscription, paid content, rewards or digital benefits. We would like to open
> an optional external one-time tip page in the user's browser. The provider
> charges no platform commission; PayPal or Stripe still deducts its ordinary
> payment-processing fee. The app receives no purchase information and includes
> no payment SDK. Does this qualify for your direct-tip exception requiring 100%
> of the contribution to reach the creator, or do processing deductions make it
> ineligible? If ineligible, does a checkout where the sender separately covers
> the processor fee qualify, provided the declared tip reaches the creator in
> full and grants no digital benefit?

Attach the actual configured checkout and fee receipts when they exist. Record
the case ID and Google's answer before claiming eligibility. Do not treat this
draft, an unsent message or a provider's zero-commission page as approval.
