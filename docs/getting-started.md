# Getting started

Speedbreaker runs on Android 8.0 or newer. You choose which apps get a breathing
pause and can change those choices at any time. There is no account to create.

## Install the APK

1. Visit [GitHub Releases](https://github.com/beejsbj/speedbreaker/releases)
   on your phone. Open a published release and download its `.apk` from
   **Assets**. The source ZIP and tar archive are for developers, not installers.
2. Open the downloaded APK. Android may ask you to allow installation from your
   browser or file manager. Grant that permission for the installation; you can
   turn it off again afterward.
3. Complete Android's installer, then open **Speedbreaker**. Keep Play Protect
   enabled. If installation is blocked, record the exact message rather than
   disabling device-wide protections.

If no release is listed, a public APK is not available yet. This project is not
currently distributed through Google Play or F-Droid and has no iOS version.

## Enable Accessibility

The first page says what Speedbreaker does; tap **Continue**. Read the disclosure
on the second page and tap **I understand — continue**. **Not now — close** closes
the app without accepting, and the pages return the next time you open it. Home
then lists two steps: Accessibility and choosing apps. You can
leave and come back at any point; finished steps stay ticked. Speedbreaker uses
Accessibility to identify visible apps and display its breathing overlay.
**For the first eight seconds, Home, Back, and Recents do not dismiss that
overlay.** Afterward, navigation and the available choices let you leave.
[Privacy details](privacy.md) explain what is observed and stored.

Tap **Turn on in Accessibility**, find **Speedbreaker** (listed as **Speedbreaker attention pauses** in 1.0.5 and earlier),
and enable it. On Samsung this is usually under **Accessibility → Installed apps**.
Speedbreaker opens again by itself once the service is on; if it does not, press Back.
On phones whose Settings supports it, the entry is scrolled into view and highlighted.

If Android shows **Restricted setting** or **Controlled by Restricted Setting**:

1. Open Android's **Settings → Apps → Speedbreaker**, or use **Open app info** in the app.
2. Open the **⋮** menu and choose **Allow restricted settings**.
3. Complete the phone's identity check yourself.
4. Return to Accessibility and enable **Speedbreaker**.

Menu names differ across Android versions and manufacturers. This is an
individual app approval, not a reason to turn off Play Protect.

## Phone permission (optional)

Breaths work without Phone permission. Without it, Speedbreaker notices a call only
once Android switches to ringtone or call audio, so a call that rings without a ringtone
(silent, vibrate or Do Not Disturb) can stay hidden behind a breath for its length (twelve seconds by default).

To close that gap, open Settings (the gear on Home), tap **Phone permission** under
**Reliability** and allow it. Through 1.0.6 the Reliability rows are on Home itself.
Android words its prompt as “make and manage phone calls”; Speedbreaker cannot make
calls. The permission lets it detect ringing calls, including silent ones, even when
their controls appear in a system notification. It checks whether a call exists; it
does not read numbers, call history, or audio.

After one denial the same row asks again. After two, Android stops showing its
prompt; the row then says so and opens app info, where **Permissions → Phone →
Allow** grants it, and Speedbreaker opens again once it is allowed.
Some third-party calling apps do not report incoming calls to Android and may not
be detected either way.

## Choose and configure apps

Tap **Choose apps** and select the apps you want a breath before. Search is available;
changes to the target selection take effect as you select or deselect apps.
Speedbreaker, the launcher, keyboards, and safety-related system apps are
excluded from the picker. Nothing is selected on a fresh installation.

With Accessibility connected, the home screen shows **One step left**
until you choose an app, then **On** with how long you have breathed so far. Open
one of those apps to try the breath.

The defaults are a twelve-second breath, check-ins every ten minutes of
continuous use, and active hours all day, every day. No detours are configured.

| To change… | Open… | What to expect |
| --- | --- | --- |
| Breath length | **Settings → Breath length** | 8–60 seconds, shared by all chosen apps. The first eight seconds always hold the overlay. |
| Repeat check-ins | An app → **Check in again** | Turn off, or choose an interval from 1 to 120 minutes. A breath on opening still applies. |
| Detour destinations | **Settings → Detours** | Choose one to four eligible apps and save, or leave detours empty. Chosen target apps cannot be detours. |
| Active hours | **Settings → Schedule → Scheduled** | One active window per day, including overnight windows. Review the initial weekday 9 AM–5 PM schedule. |
| One app's hours | An app → **Schedule** | Follow the default, stay always on, or set custom hours that replace the default. |
| One app's detours | An app → **Detours → Custom** | Replace the default set with one to four apps, or leave the custom set empty to show none for that app. Choose Default to inherit again. |
| Stop breaths for one app | An app → **Stop breaths for…** | Remove it from the chosen apps. |

In the rest of this guide, **Settings** means Speedbreaker's own, behind the gear at the
top of Home. Versions through 1.0.6 have no Settings page: breath length, detours and
hours sit together on one **Defaults** page, and Reliability, Help and Check for updates
are on Home itself.

Schedules use the phone's local time. Days without a scheduled window are off.
Outside the effective schedule, the app opens freely; entering an active window
while it is visible starts a breath.

## During a breath

For eight seconds, only the breathing screen is available. At eight seconds:

- **Leave** returns to your home screen and ends that app's session.
- A configured **detour** opens that app and ends the target session.
- **Pause 15 min** skips intervention for that target for fifteen minutes,
  using one of its two daily allowances. Notifications must be available.
- **Continue** becomes available once the whole configured breath is complete.

The reflection, “What are you here for?”, asks for no input and stores no answer.
After Continue, visible screen-on use counts toward the next check-in, including
split-screen and picture-in-picture. Leaving briefly pauses that interval;
returning within ten seconds resumes it. A longer absence starts a fresh
opening breath. Leave and detours end the session immediately.

A fifteen-minute pause appears under **Active pauses** on Speedbreaker’s home
screen, with **End now** for each app. Its notification provides the same action
and opens Speedbreaker when tapped. If you dismiss the notification, the in-app
control remains available; ending one early still uses it up. Ending or
expiring it resumes enforcement immediately if the target is visible, or on its
next opening otherwise. Allowances reset each local day. An active pause keeps
its original expiry even if it crosses midnight.

One Hand Operation+ edge gestures do not work while the overlay is visible.
Use Leave, a detour, or ordinary navigation after eight seconds. Android-reported
calls, the dialer, lock screen, screen-off, and Android Settings take priority.
A call-state check runs during each observation and on the service’s 250 ms tick;
this avoids the eight-second lock delay, but is not a guaranteed response deadline.
After a call ends, a still-visible target can receive a fresh opening breath. Device verification is still limited; see
[the README](../README.md#privacy-and-device-support).

## Troubleshooting

### The home screen says “Off for now” or “Needs attention”

**Off for now** means Accessibility is disabled. **Needs attention** means the
service has not connected or has reported a problem. Follow the displayed
message. If Accessibility is already on, turn the Speedbreaker service off and
on again, then return to the app to check its status. Settings remain editable.

If local settings cannot be loaded or saved, Speedbreaker stops enforcement
and reports the failure. Resolve the phone's storage problem and restart the
app. Clearing app storage is a reset and deletes your choices and pause state.

### A chosen app opens without a breath

Check that the app is still selected, Accessibility is connected, and its
schedule is currently active. Call-state access must be allowed, and Android
must not be reporting a call or communication audio. A fifteen-minute Pause suppresses intervention;
a quick return after Continue can also resume a session without another breath.

On Samsung, use **Settings → Reliability → Battery and background** to open Speedbreaker's
app info, then set **Battery → Unrestricted** if available. Background behavior
varies by phone; extended idle reliability has not been verified on release
1.0.1. If breaths keep going missing, report the phone model and exact conditions.

### Pause is unavailable

Open **Settings → Reliability → Notifications** and allow Speedbreaker's
notifications, including the active-pauses channel. Pause is unavailable when
notifications are blocked, or when that app has used both daily allowances.
Turning notifications off also ends active pauses when the service observes
the change. Breathing interventions work without notification permission.

### Detour buttons are missing

The detour row is optional and appears with one to four valid destinations.
Choose up to four apps under **Settings → Detours**, or in that app's Custom detours, and save.
An empty Custom set shows none for that app; Default inherits the global set. An
app selected as a target cannot also be a detour.

### Installation or an update fails

Check the release's minimum Android version and that you downloaded the APK.
Include the exact Android installer message in a bug report. A locally built
debug APK may have a different signing identity from a published APK, preventing
an in-place update. Do not uninstall as a first troubleshooting step: uninstalling
deletes local settings and pause state.

## Update Speedbreaker

Download a newer published APK from
[GitHub Releases](https://github.com/beejsbj/speedbreaker/releases) and open it
through Android's installer. Releases with the same package and compatible
signing identity can update the existing app while retaining settings. Check
the version and Accessibility connection after updating.

From 1.0.4 the GitHub APK can also update itself. **Check for updates** in Settings
(at the foot of Home through 1.0.6) asks GitHub whether a newer release exists. If one does, tap **Update**:
Speedbreaker downloads it, checks it against the release's checksum, and hands it
to Android's installer, which asks you to confirm. An optional automatic check is
off until you turn it on beside that button; it then asks GitHub at most once a day, and
only when you open Speedbreaker, never in the background. Versions 1.0.3
and earlier have no updater; install a newer APK over them by hand once.

## Stop or remove Speedbreaker

Turn off **Speedbreaker** (listed as **Speedbreaker attention pauses** in 1.0.5 and earlier)
in Android Accessibility settings. That stops intervention and keeps your settings. You can uninstall Speedbreaker
normally from app info; uninstalling removes its local app data. There is no
cloud copy to restore from.

For a problem not covered here, [open a bug report](https://github.com/beejsbj/speedbreaker/issues/new/choose).
Include only the information needed to reproduce it, with private details removed.
