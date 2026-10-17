# Privacy policy

Applies to Android Speedbreaker through **1.0.4**, including its GitHub,
community (F-Droid) and Google Play builds. Neither store build has been released.
Last reviewed: **October 17, 2026**. Publisher: **Burooj (beejsbj)**.

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

The 1.0.2 candidate also reads native SystemUI window type, display and touchable
geometry to expose a full-display system panel above the breathing surface.
These are transient window metadata, not notification or screen contents.

Current visibility and continuous-use timers are held in memory. They are not
saved as a usage log. Screen-off time does not count toward check-ins.
A single on-device flag records only that Speedbreaker muted the music stream during a breath, so it can unmute after a crash; it is cleared when the breath ends and holds no app or time information.

## Call safety

Speedbreaker checks Android’s aggregate call-state boolean through
`TelecomManager.isInCall()`, including silent ringing and calls registered by
third-party calling apps. It also checks the current audio mode as a secondary
signal for communication audio. These values are not stored. When a safety check
holds back or ends a breath, the aggregate result (booleans and the audio-mode
number) is written to Android's local diagnostic log described below.

Android calls the required runtime permission **Phone**. Speedbreaker uses it
only for the call-state check; it does not request contacts, call logs, microphone
access, or permission to place or answer calls. It does not read phone numbers,
caller identity, call history, notification contents, or audio.

If permission is denied or revoked, or a detectable system-service error occurs,
breathing overlays stay off. Existing settings and pause allowances remain.
Android’s APIs cannot identify every failure, and calling apps that do not
register calls with Android may not be detected before communication audio starts.
See [Android’s call-state API](https://developer.android.com/reference/android/telecom/TelecomManager#isInCall()).

## Installed apps

The picker queries Android for launchable apps and displays their labels and
icons. It also identifies the launcher, keyboards, and dialer to exclude them
from targets. Your selected targets and redirect destinations are saved as app
package names; the app does not upload your installed-app list.

## What stays on the phone

Speedbreaker stores these records in its private local app database:

- Your acknowledgment of the onboarding disclosure.
- Breath length, selected app package names, check-in settings, active schedules,
  and redirect package names.
- A running total of the seconds you have breathed and the number of breaths, overall
  and for the current local day. Home shows it, and **Reset breathing total** there sets it back
  to zero. It is never sent anywhere.
- A rolling pause-state record for each relevant app: the allowance day, how many
  pauses were used, and the active pause's expiry time.
- GitHub build only: whether **Check for updates automatically** is on, and the date
  of the last automatic check.

The breathing total holds four numbers: total time, total breaths, today's date, and
today's time. It is not per app and has no timestamps, list of breaths, or history, so
it cannot say which app a breath was for or when it happened. There is no timeline of
app usage, daily usage total, or archive of pause events.
Pause-state dates and allowances are reconciled as the service runs; an active
pause can outlast midnight without receiving a new fifteen-minute duration.
The reflection asks for no input, so no answer is collected or stored.

## GitHub build: update checks

This section applies only to the APK downloaded from GitHub Releases (1.0.4 and
later). The F-Droid (community) and Google Play builds never check for updates and
cannot reach the network.

Speedbreaker contacts GitHub only when you tap **Check for updates** on Home, or,
if you turn on **Check for updates automatically** in Defaults (off by default),
at most once a day when you open the app. It asks
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
| Phone state | Check whether Android reports a ringing, active, dialing, or held call. Breaths stay off if this permission is denied or revoked. |
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

Disable **Speedbreaker attention pauses** in Android Accessibility settings to
stop the service. Your saved settings remain on the phone. Uninstall the app,
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
