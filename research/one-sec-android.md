# one sec on Android — mechanism note

**Accessed:** 2026-08-20. **Scope:** Android only, except where a source explicitly labels a feature cross-platform. Sources are first-party one sec material, its official Google Play listing, and Android documentation for facilities that one sec itself discloses.

## Short verdict

one sec’s documented Android core is **friction at a selected app opening**, not a documented daily-budget system: it says it uses Android’s Accessibility Service API to detect and intervene on user-selected target apps, then presents a breathing exercise before the user continues. Its support material also documents a legacy App Usage-permission fallback and an App Overlay permission during setup. [Google Play listing](https://play.google.com/store/apps/details?id=wtf.riedel.onesec) · [Android setup](https://tutorials.one-sec.app/en/articles/3034754) · [permission fallback](https://tutorials.one-sec.app/en/articles/3148866)

There are optional time-based features—re-intervention after a chosen interval, a quick-switch grace interval, schedules, and time tracking—but I found **no Android primary source that documents enforcing a cumulative calendar-day allowance**. That is an evidence boundary, not evidence that no internal counter exists. [Android re-intervention](https://tutorials.one-sec.app/en/articles/3035202) · [intentional app switching](https://tutorials.one-sec.app/en/articles/3310146) · [Android Pro matrix](https://tutorials.one-sec.app/en/articles/3036418)

## Documented Android setup and mechanism

1. Setup asks the user to enable one sec’s Accessibility Service, accept Android’s prompt, and then grant additional permission(s), including App Overlay; only then does the tutorial direct the user to select apps. It says the newer Android “Accessibility Shortcut” toggle should remain off. [Android setup](https://tutorials.one-sec.app/en/articles/3034754)
2. The official Play listing says the app uses the **Accessibility Service API** “to detect and intervene user-selected target apps.” one sec’s support describes this as its preferred path: more stable, faster, and receiving passive events rather than actively polling activity data. [Google Play listing](https://play.google.com/store/apps/details?id=wtf.riedel.onesec) · [permission fallback](https://tutorials.one-sec.app/en/articles/3148866)
3. A user may decline Accessibility by choosing “ignore accessibility service” on one sec’s permission screen; the app then prompts for **App Usage permission**, which its support calls the older/legacy approach. [permission fallback](https://tutorials.one-sec.app/en/articles/3148866)
4. Android manufacturer battery management can stop one sec in the background, after which “it won’t work anymore”; one sec documents vendor-specific background/autostart exemptions and its current troubleshooting asks users to turn battery optimization off. This is a reliability prerequisite, not a new data permission. [background settings](https://tutorials.one-sec.app/en/articles/3310402) · [Android troubleshooting](https://tutorials.one-sec.app/en/articles/3262274)

Android documentation explains only the **capabilities**, not one sec’s private implementation: an `AccessibilityService` can run in the background and receive callbacks for UI state-transition events; `UsageStatsManager` can query usage/events with `PACKAGE_USAGE_STATS`; and Android documents third-party overlays as drawing over apps. These support the disclosed facilities, but do **not** prove which event types, polling cadence, window-content access, or overlay rendering path one sec uses. [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) · [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) · [Android overlay guidance](https://developer.android.com/security/fraud-prevention/activities)

## Intervention loop

The Android-specific, directly documented loop is:

1. The user picks target apps after permission setup. [Android setup](https://tutorials.one-sec.app/en/articles/3034754)
2. Opening a distracting/target app automatically triggers one sec; the Play listing says it forces a deep breath whenever such an app is opened. [Google Play listing](https://play.google.com/store/apps/details?id=wtf.riedel.onesec)
3. The intervention is a small exercise shown before the user can continue; on Android, the documented customisation is the breathing exercise’s duration and displayed phrase. [intervention customisation](https://tutorials.one-sec.app/en/articles/3310978)
4. Optionally, **Re-Intervention** can interrupt a target app after a set time and require another intervention to resume. Android setup is per target app and sets the maximum time interval available when opening it. [Android re-intervention](https://tutorials.one-sec.app/en/articles/3035202)
5. Optionally, **Intentional App Switching** gives a chosen re-open interval (for example, one minute) so a deliberate quick return is not interrupted. [intentional app switching](https://tutorials.one-sec.app/en/articles/3310146)

The general “Immediate / Delayed / Always Ask” article describes an immediate opening intervention, a short initial-use period before an unlock intervention, and asking for intended time on opening; it is not labelled as an Android-specific guide. Its mechanics are useful context, but should not be read as proof that every mode is currently available on Android. [mode documentation](https://tutorials.one-sec.app/en/articles/3974530)

## Optional usage and limit features

- The June 2026 Android Pro matrix lists: breathing exercise, re-intervention, unlimited target apps, intentional app switching, and **time tracking**. It marks different intervention types, “Don’t Get Lost” notifications, website blocking, strict block sessions, and strict porn blocker as **N/A** for Android. [Android Pro matrix](https://tutorials.one-sec.app/en/articles/3036418)
- Android intervention schedules are documented as **Beta**: choose apps, active hours, and optionally different schedules per day of week. This is time-of-day policy, not a daily-usage budget. [Android scheduling](https://tutorials.one-sec.app/en/articles/3311554)
- Android browser-extension website intervention is explicitly unavailable because Android browsers do not offer the needed extension functionality. Do not transpose the iOS Safari extension or desktop web behavior to Android. [website-intervention support](https://tutorials.one-sec.app/en/articles/3309954)

## Privacy and data handling relevant to the mechanism

The current Play listing says its Accessibility use detects/intervenes selected apps, that personal information is not collected, and that data remains offline/on-device; its Play Data Safety declaration says no data is collected or shared. [Google Play listing](https://play.google.com/store/apps/details?id=wtf.riedel.onesec)

The broader product privacy page says data is offline by default and that optional support, feedback, purchase, or sync features can require processing; it names iCloud sync, which is not evidence about Android. [one sec privacy page](https://one-sec.app/privacy/)

**Unresolved disclosure tension:** the privacy page links to one sec’s general policy, which says the application collects some personal data and lists TelemetryDeck analytics (including device information and device logs). It does not attribute that processing to Android, to selected-app openings, or to a particular optional feature. The public Play declaration and linked general policy therefore cannot be reconciled precisely from the available first-party material. [linked privacy policy](https://www.iubenda.com/privacy-policy/40526861) · [Google Play listing](https://play.google.com/store/apps/details?id=wtf.riedel.onesec)

## Verified vs. inferred

| Claim | Status | Basis / boundary |
| --- | --- | --- |
| one sec detects and intervenes on user-selected Android target apps through Accessibility Service API. | **Verified** | Explicit Play listing claim. |
| Setup discloses Accessibility, App Overlay, and App Usage fallback. | **Verified** | Explicit Android support articles. |
| Accessibility is event-driven and the legacy route actively polls activity data. | **Verified as one sec’s description** | The help article says this; no event type/cadence is disclosed. |
| one sec displays its intervention *by* drawing an app-overlay window. | **Inferred; not established** | Overlay permission is requested and Android permits overlays, but one sec never specifies its rendering implementation. |
| Re-intervention and quick-switch policy operate on per-app intervals. | **Verified** | Android-specific setup instructions describe each target app and selected maximum/grace intervals. |
| Android enforces a cumulative daily usage allowance. | **Not documented** | Time tracking and schedules are documented; a daily aggregate threshold is not. The Android usage API’s ability to aggregate daily data is not evidence that one sec does so. |

## Lessons for Speedbreaker

1. Make the core proposition an **opening-time interruption**, with optional in-session re-friction; a daily counter is a separate policy, not a substitute for the decisive moment.
2. Keep detection, intervention presentation, and policy separate. Android facilities can enable a design but should not be represented as proof of its implementation.
3. Treat permission consent and OEM background survival as first-class setup/reliability work; explain the benefit and provide recovery guidance when the intervention stops firing.
4. Be explicit about platform scope. Android’s documented intervention surface is the configurable breathing flow; avoid promising iOS Screen Time/Shortcuts, varied interventions, strict sessions, or web blocking on Android without Android-specific evidence.
5. If usage data leaves device, state exactly what, why, and on which platform/feature. “Offline by default” and a broad analytics policy are not sufficiently precise together.

## Sources

- [one sec — official Google Play listing](https://play.google.com/store/apps/details?id=wtf.riedel.onesec)
- [one sec — Initial permission setup on Android](https://tutorials.one-sec.app/en/articles/3034754)
- [one sec — Accessibility permission alternative](https://tutorials.one-sec.app/en/articles/3148866)
- [one sec — Android re-intervention](https://tutorials.one-sec.app/en/articles/3035202)
- [one sec — Intentional App Switching](https://tutorials.one-sec.app/en/articles/3310146)
- [one sec — Scheduling of interventions](https://tutorials.one-sec.app/en/articles/3311554)
- [one sec — Intervention customisation](https://tutorials.one-sec.app/en/articles/3310978)
- [one sec — Immediate, Delayed, and Always Ask modes](https://tutorials.one-sec.app/en/articles/3974530)
- [one sec — Pro feature matrix](https://tutorials.one-sec.app/en/articles/3036418)
- [one sec — Android background settings](https://tutorials.one-sec.app/en/articles/3310402)
- [one sec — Android intervention troubleshooting](https://tutorials.one-sec.app/en/articles/3262274)
- [one sec — Website intervention platform support](https://tutorials.one-sec.app/en/articles/3309954)
- [one sec — Privacy page](https://one-sec.app/privacy/)
- [one sec — linked general privacy policy](https://www.iubenda.com/privacy-policy/40526861)
- [Android Developers — AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Android Developers — UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [Android Developers — overlay guidance](https://developer.android.com/security/fraud-prevention/activities)
