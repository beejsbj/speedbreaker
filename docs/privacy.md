# Privacy policy

Applies to Android Speedbreaker through **1.0.8**, including its GitHub,
community (F-Droid) and Google Play builds. Neither store build has been released.
Last reviewed: **October 17, 2026**; corrected **November 10, 2026** (window data and notification-shade wording); updated **November 11, 2026** (Phone permission is optional) and **November 12, 2026** (what 1.0.7 and later count beside the breathing total). Publisher: **Burooj (beejsbj)**.

Speedbreaker runs on your Android phone. It has no account, analytics, ads,
cloud sync, billing SDK or developer backend. The community (F-Droid) and Google
Play builds have no Internet permission. From 1.0.4 the APK on GitHub Releases can
contact GitHub, only to check for and download its own updates; see
[GitHub build: update checks](#github-build-update-checks). No build sends your
settings or app activity to the developer or anyone else. Optional support opens
an external website; it does not unlock any feature.

## What Accessibility observes

With your consent and Android's Accessibility service enabled, Speedbreaker
reads the package names of visible application windows and Accessibility window
events. It uses those app identities to recognize chosen targets, handle
navigation, and yield to system screens such as Settings and the dialer.
This includes identifying relevant non-target apps; observation is not limited
to the list you select.

Android grants broad capability through Accessibility, including access to
window content. Speedbreaker's implementation reads window-root package names
and event metadata. It does not traverse screen text, read messages or typed
input, capture screenshots, or record the contents of other apps.

It also reads each window's id number and its kind (for example app, system, or
keyboard), so it can tell an app screen or Recents from a system popup such as
the volume panel. These are kept only in memory and never saved; they are not
screen contents. It does not read window positions or sizes.

A breath covers the whole screen. For its first eight seconds Back, Home and
Recents do not close it, and the notification shade and Quick Settings cannot be
pulled down over it.

Current visibility and continuous-use timers are held in memory. They are not
saved as a usage log. Screen-off time does not count toward check-ins.
A single on-device flag records only that Speedbreaker muted the music stream during a breath, so it can unmute after a crash; it is cleared when the breath ends and holds no app or time information.

## Call safety

With Phone permission, Speedbreaker checks Android’s aggregate call-state boolean
through `TelecomManager.isInCall()`, including silent ringing and calls registered by
third-party calling apps. With or without it, Speedbreaker checks the current audio
mode, which changes for ringtone and communication audio. These values are not stored. When a safety check
holds back or ends a breath, the aggregate result (booleans and the audio-mode
number) is written to Android's local diagnostic log described below.

Android calls the runtime permission **Phone**. It is optional from 1.0.6 (earlier
versions kept breaths off without it), and is offered under Reliability in Settings
(on Home through 1.0.6), not during setup. Speedbreaker uses it
only for the call-state check; it does not request contacts, call logs, microphone
access, or permission to place or answer calls. It does not read phone numbers,
caller identity, call history, notification contents, or audio.

Without the permission, breaths still run and the call-state check is skipped. A call
that rings without a ringtone (silent, vibrate or Do Not Disturb) does not change the audio mode, so it is not seen
and can stay hidden behind a breath for the breath’s length. If a detectable
system-service error occurs, breathing overlays stay off; existing settings and pause
allowances remain. Android’s APIs cannot identify every failure, and calling apps that do not
register calls with Android may not be detected before communication audio starts.
See [Android’s call-state API](https://developer.android.com/reference/android/telecom/TelecomManager#isInCall()).

## Installed apps

The picker queries Android for launchable apps and displays their labels and
icons. It also identifies the launcher, keyboards, and dialer to exclude them
from targets. Your selected targets and detour destinations are saved as app
package names; the app does not upload your installed-app list.

## What stays on the phone

Speedbreaker stores these records in its private local app database:

- Your acknowledgment of the onboarding disclosure.
- Breath length, selected app package names, check-in settings, active schedules,
  and detour package names.
- A running total of the seconds you have breathed and the number of breaths, overall
  and for the current local day. Home shows it, and **Reset breathing total** in Settings
  (at the foot of Home through 1.0.6) sets it back to zero. It is never sent anywhere.
- From 1.0.7, three more running records beside that total, counted from your first
  breath on such a version (and again from zero after a reset):
  - how many breaths ended each way: you left, continued, took a detour, took
    fifteen minutes off, or the breath ended by something other than you (a call, the
    lock screen, your schedule);
  - for each app currently chosen as a detour, how many times you took it;
  - the time breathed on each of the last seven local days, and the day counting began.

  **Your breaths**, opened from Home, shows them. **Reset breathing total** clears them
  with the total, and a detour's count is deleted when you remove that app from every
  detour list. A daily total older than seven days is deleted the next time Speedbreaker
  starts or counts a breath. They are never sent anywhere.
- A rolling pause-state record for each relevant app: the allowance day, how many
  pauses were used, and the active pause's expiry time.
- GitHub build only, in a private preferences file beside the database: whether
  the automatic update check is on, and the date of the last automatic check.

The breathing total holds four numbers: total time, total breaths, today's date, and
today's time. Through 1.0.6 that is the only usage figure, with no per-app count and no
daily history. 1.0.7 and later add the records listed above: five counts, a count for each
chosen detour app, and seven daily totals.

In every version these totals hold no list of breaths and no time of day, and no field
names the chosen app a breath was for. A detour count says how often you went to that
app from a breath. If an app is a detour for only one of your chosen apps, its count is
also a count of breaths on that one app that ended in that detour. There is no timeline
of app usage or archive of pause events.
Pause-state dates and allowances are reconciled as the service runs; an active
pause can outlast midnight without receiving a new fifteen-minute duration.
The reflection asks for no input, so no answer is collected or stored.

## GitHub build: update checks

This section applies only to the APK downloaded from GitHub Releases (1.0.4 and
later). The F-Droid (community) and Google Play builds never check for updates and
cannot reach the network.

Speedbreaker contacts GitHub only when you tap **Check for updates**, or, if you turn
on the automatic check beside it (off by default), at most once a day when you open the
app. Both are under Updates in Settings; through 1.0.6 the button is at the foot of Home
and the switch is in Defaults. It asks
`api.github.com/repos/beejsbj/speedbreaker/releases/latest` for the latest release.
The request carries no account, token, cookie, device or install identifier; the
only header Speedbreaker sets is `User-Agent: Speedbreaker/<version>`. It sends
nothing about your settings, chosen apps, breaths or use. As with any web request,
GitHub sees your IP address and the request itself, under
[GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

If you tap **Update**, Speedbreaker downloads that release's APK and its SHA-256
file from `github.com` (which serves them from GitHub's download host) into its
private cache, checks the file against that digest, and hands it to Android's
installer. Android asks you to confirm and installs it only if it is signed by the
same developer key. The cached copy is deleted after hand-off, cancellation or a
failed check. Nothing runs in the background and nothing happens while a breath is
showing.

## Diagnostic log

Speedbreaker writes short decision codes to Android's local log buffer (logcat) so a
problem can be explained: why a breath started or did not, and why it ended. Lines hold
reason codes, booleans, small numbers and elapsed times, and name only the target apps
you chose; never other apps, window titles or screen content. These logs stay in
Android's local log buffer only. Speedbreaker does not save them to a file, upload them,
or send them anywhere. See [Device diagnostics](build-and-test.md#device-diagnostics).

## Other Android permissions and surfaces

| Permission or capability | Purpose |
| --- | --- |
| Accessibility service | Identify visible apps, show the breathing overlay, and perform the user's navigation choices. You enable it in Android Settings. |
| Phone state (optional) | Check whether Android reports a ringing, active, dialing, or held call. Without it, breaths run and a call ringing without a ringtone (silent, vibrate or Do Not Disturb) is not seen. |
| Notifications | Show each active fifteen-minute pause and its **End now** action. Pause requires notifications to be available. |
| Receive boot completed | Support recovery of locally stored pause state after the phone restarts. |
| Internet (GitHub build only) | Check GitHub for a newer release and download it, only when you ask or have turned on the daily check. |
| Install unknown apps (GitHub build only) | Hand a downloaded update to Android's installer. Android asks you to allow this once, and to confirm each install. |

While a breath is showing, Speedbreaker asks Android for audio focus so other
apps can pause their media. This needs no permission, plays no sound, and reads
no audio or media information; it is released when the breath ends.

Pause notifications include the paused app's name and the pause end time.
Depending on your Android notification settings, these may be visible on the
lock screen. Adjust Speedbreaker's notification visibility in Android Settings
if needed.

The app requests neither Usage Access nor a separate “display over other apps”
permission. Its overlay belongs to the Accessibility service. Android cloud
backup and device transfer are disabled for app data in the manifest and data
extraction rules.

## Stop observation or delete data

Disable **Speedbreaker** (listed as **Speedbreaker attention pauses** in 1.0.5 and earlier)
in Android Accessibility settings to stop the service. Your saved settings remain on the phone. Uninstall the app,
or clear its storage in Android Settings, to remove its local data. Clearing
storage also resets consent, chosen apps, pause allowances, and the breathing total. There is no
Speedbreaker cloud backup or account to delete.

## Optional support and payments

### GitHub and community builds

The optional **Buy me a coffee** action opens
[ko-fi.com/beejsbj](https://ko-fi.com/beejsbj) outside Speedbreaker.
That website and its payment providers handle any visit or contribution under
their own privacy terms. Speedbreaker sends no settings or app-usage data with
the link, embeds no payment SDK, and does not receive or store payment details.
Contributing does not unlock features.

### Google Play candidate

This candidate has no active support link: a direct-tip destination and its
eligibility under Google Play's payment rules have not yet been verified.
It contains no native payment library, receives no payment receipts or tokens,
and grants no content, feature, badge, or other benefit for a contribution.

If a verified external direct-tip page is configured in a later build, it opens
in your browser without app settings, activity, identifiers or other personal
data in the URL. The website and its payment providers process the contribution
under their own privacy terms. Speedbreaker itself does not receive or store
card information, transaction records or payment status. The policy will identify
the actual provider before that flow is enabled.

External sites and payment providers retain their own visit/transaction records.
Clearing app storage does not delete a website account or request a payment
refund. Use the actual provider's controls for those records and requests.

## Downloads, reports and contact

GitHub hosts this repository, releases, and issue reports. Visiting GitHub or
posting there is separate from using the app; the GitHub build's own update checks
are described above. Anything you include in
a public issue is public; redact notifications, account details, personal app
choices, and other private information from screenshots and logs.

For a privacy or security concern, follow the
[security reporting instructions](../SECURITY.md). Ordinary questions and bugs
can use [GitHub Issues](https://github.com/beejsbj/speedbreaker/issues/new/choose).

## Implementation references

These source files define the behavior described above:

- [Manifest](../app/src/main/AndroidManifest.xml) and
  [Accessibility configuration](../app/src/main/res/xml/accessibility_service_config.xml).
- [Window observation](../app/src/main/java/dev/burooj/speedbreaker/observation/WindowObservationReader.kt)
  and [service events](../app/src/main/java/dev/burooj/speedbreaker/observation/SpeedbreakerService.kt).
- [App discovery](../app/src/main/java/dev/burooj/speedbreaker/presentation/AppDiscovery.kt).
- [Stored settings and pause state](../app/src/main/java/dev/burooj/speedbreaker/model/Settings.kt)
  and [local database access](../app/src/main/java/dev/burooj/speedbreaker/persistence/SpeedbreakerRepository.kt).
- [Call-state checks](../app/src/main/java/dev/burooj/speedbreaker/observation/CallSafety.kt).
- GitHub build updater: [its manifest](../app/src/github/AndroidManifest.xml),
  [network requests](../app/src/github/java/dev/burooj/speedbreaker/updates/GitHubClient.kt)
  and [update flow](../app/src/github/java/dev/burooj/speedbreaker/updates/UpdateController.kt).
- [Notification content](../app/src/main/java/dev/burooj/speedbreaker/observation/PauseNotifications.kt)
  and [backup exclusions](../app/src/main/res/xml/data_extraction_rules.xml).
