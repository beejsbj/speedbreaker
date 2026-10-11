# Optional redirect counts

Owner [issue #9](https://github.com/beejsbj/speedbreaker/issues/9) explicitly
superseded the old exactly-four requirement. [PR #10](https://github.com/beejsbj/speedbreaker/pull/10)
merged at `df8808eee2511f42cc7b9e984841f2c5990a7808` on October 11,
19:50 UTC; GitHub then closed the issue. Its branch and worktree are preserved.

The change supports zero to four global and per-app destinations, retaining order
and the existing schema. A null override inherits; an explicit empty override
disables redirects. Target removal retains the other destinations. Invalid or
oversized runtime sets expose no launch choices. The service change is limited
to its shared resolver import, overlay redirect resolution and count constant.
Breathing, navigation, schedules, calls and signing behavior were not changed.

## Independent review

Actual harness records confirm **gpt-6-astra / high** and
**gpt-6.1-sol / xhigh**, each inspecting the fixed diff against main `769b403`.
Both found the same P3: Defaults still described four destinations unconditionally.
Commit `6f453d59137899ed3b61c51ae1b21bd1945f89ff` corrected that one line.
Both independently verified the correction; no other actionable findings remained.

- Astra: [initial review](https://github.com/beejsbj/speedbreaker/pull/10#pullrequestreview-5484681392)
  and [resolved follow-up](https://github.com/beejsbj/speedbreaker/pull/10#pullrequestreview-5484688534).
- Sol 6.1: [initial review](https://github.com/beejsbj/speedbreaker/pull/10#pullrequestreview-5484690676)
  and [resolved follow-up](https://github.com/beejsbj/speedbreaker/pull/10#pullrequestreview-5484697134).

## Actual verification and limits

[Hosted run 38169359412](https://github.com/beejsbj/speedbreaker/actions/runs/38169359412)
passed for head `6f453d5`: unsigned release build, **94 tests with zero
failures/errors/skips**, release lint **zero errors / 32 warnings**.
PR test checkout: `60d85fc6f2a045bfaf16a4678e94dbe3a5dcf839`.
Earlier [run 38168928943](https://github.com/beejsbj/speedbreaker/actions/runs/38168928943)
passed the same counts at `57cc89d`; the parent independently parsed that report.
The worker independently parsed the new-head XML reports.

Source review and Robolectric component tests do not establish on-device
Accessibility-service redirect behavior. The changed candidate has not been
installed on the owner's phone. No local Gradle, emulator, signing or release
publication occurred. These main-based code-5 outputs were verification only;
the store branch integrates the change into unreleased 1.0.2 / code 6.
The immutable 1.0.1 APK remains unchanged.
