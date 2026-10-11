# Privacy

Applies to Speedbreaker **1.0.0**. Last reviewed: **October 11, 2026**.

Speedbreaker runs on your Android phone. It has no account, analytics, ads,
cloud sync, or Internet permission. The app does not send your settings or app
activity to the developer.

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

Current visibility and continuous-use timers are held in memory. They are not
saved as a usage log. Screen-off time does not count toward check-ins.

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
- A rolling pause-state record for each relevant app: the allowance day, how many
  pauses were used, and the active pause's expiry time.

There is no timeline of app usage, daily usage total, or archive of pause events.
Pause-state dates and allowances are reconciled as the service runs; an active
pause can outlast midnight without receiving a new fifteen-minute duration.
The reflection asks for no input, so no answer is collected or stored.

## Other Android permissions and surfaces

| Permission or capability | Purpose |
| --- | --- |
| Accessibility service | Identify visible apps, show the breathing overlay, and perform the user's navigation choices. You enable it in Android Settings. |
| Notifications | Show each active fifteen-minute pause and its **End now** action. Pause requires notifications to be available. |
| Receive boot completed | Support recovery of locally stored pause state after the phone restarts. |

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
storage also resets consent, chosen apps, and pause allowances. There is no
Speedbreaker cloud backup or account to delete.

## Downloads and reports

GitHub hosts this repository, releases, and issue reports. Visiting GitHub or
posting there is separate from using the offline app. Anything you include in
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
- [Notification content](../app/src/main/java/dev/burooj/speedbreaker/observation/PauseNotifications.kt)
  and [backup exclusions](../app/src/main/res/xml/data_extraction_rules.xml).
