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

Read the welcome page and tap **I understand — continue**. Speedbreaker uses
Accessibility to identify visible apps and display its breathing overlay.
**For the first eight seconds, Home, Back, and Recents do not dismiss that
overlay.** Afterward, navigation and the available choices let you leave.
[Privacy details](privacy.md) explain what is observed and stored.

Tap **Turn on in Accessibility**, find **Speedbreaker attention pauses**, and
enable it. On Samsung this is usually under **Accessibility → Installed apps**.
Return to Speedbreaker afterward.

If Android shows **Restricted setting** or **Controlled by Restricted Setting**:

1. Open **Settings → Apps → Speedbreaker**, or use **Open app info** in the app.
2. Open the **⋮** menu and choose **Allow restricted settings**.
3. Complete the phone's identity check yourself.
4. Return to Accessibility and enable **Speedbreaker attention pauses**.

Menu names differ across Android versions and manufacturers. This is an
individual app approval, not a reason to turn off Play Protect.

## Allow call-state access

After Accessibility connects, tap **Allow call-state access** and allow Android’s
**Phone** permission. This lets Speedbreaker detect ringing calls, including
silent ones, even when their controls appear in a system notification. It checks
whether a call exists; it does not read numbers, call history, or audio.

Breaths stay off if this permission is denied or revoked. To change a previous
denial, use the same button to open app info, then **Permissions → Phone**.
Android-reported calls and communication audio suspend breaths. Some third-party
calling apps do not report incoming calls to Android and may not be detected.

## Choose and configure apps

Tap **Choose apps** and select the apps you want to pause. Search is available;
changes to the target selection take effect as you select or deselect apps.
Speedbreaker, the launcher, keyboards, and safety-related system apps are
excluded from the picker. Nothing is selected on a fresh installation.

With Accessibility connected and call-state access allowed, the home screen shows **Ready when you are**
until you choose an app, then **On for … apps**. Open one of those apps to try
the breath.

The defaults are a twelve-second breath, check-ins every ten minutes of
continuous use, and active hours all day, every day. No redirects are configured.

| To change… | Open… | What to expect |
| --- | --- | --- |
| Breath length | **Defaults → Breath** | 8–60 seconds, shared by all chosen apps. The first eight seconds always hold the overlay. |
| Repeat check-ins | An app → **Check in again** | Turn off, or choose an interval from 1 to 120 minutes. Opening pauses still apply. |
| Redirect destinations | **Defaults → Redirects** | Choose exactly four eligible apps and save, or leave redirects empty. Chosen target apps cannot be redirects. |
| Active hours | **Defaults → Schedule → Scheduled** | One active window per day, including overnight windows. Review the initial weekday 9 AM–5 PM schedule. |
| One app's hours | An app → **Schedule** | Follow the default, stay always on, or set custom hours that replace the default. |
| One app's redirects | An app → **Redirects → Custom** | Replace all four defaults, or leave the custom set empty to show none for that app. |
| Stop pausing one app | An app → **Stop pausing…** | Remove it from the chosen apps. |

Schedules use the phone's local time. Days without a scheduled window are off.
Outside the effective schedule, the app opens freely; entering an active window
while it is visible starts a pause.

## During a breath

For eight seconds, only the breathing screen is available. At eight seconds:

- **Leave** returns to your home screen and ends that app's session.
- A configured **redirect** opens that app and ends the target session.
- **Pause 15 min** skips intervention for that target for fifteen minutes,
  using one of its two daily allowances. Notifications must be available.
- **Continue** becomes available once the whole configured breath is complete.

The reflection, “What are you here for?”, asks for no input and stores no answer.
After Continue, visible screen-on use counts toward the next check-in, including
split-screen and picture-in-picture. Leaving briefly pauses that interval;
returning within sixty seconds resumes it. A longer absence starts a fresh
opening breath. Leave and redirects end the session immediately.

A fifteen-minute pause appears under **Active pauses** on Speedbreaker’s home
screen, with **End now** for each app. Its notification provides the same action
and opens Speedbreaker when tapped. If you dismiss the notification, the in-app
control remains available; ending a pause does not restore its daily token. Ending or
expiring it resumes enforcement immediately if the target is visible, or on its
next opening otherwise. Allowances reset each local day. An active pause keeps
its original expiry even if it crosses midnight.

One Hand Operation+ edge gestures do not work while the overlay is visible.
Use Leave, a redirect, or ordinary navigation after eight seconds. Android-reported
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

On Samsung, use **Reliability → Battery and background** to open Speedbreaker's
app info, then set **Battery → Unrestricted** if available. Background behavior
varies by phone; extended idle reliability has not been verified on release
1.0.1. If it keeps missing pauses, report the phone model and exact conditions.

### Pause is unavailable

Open **Reliability → Pause notifications** and allow Speedbreaker's
notifications, including the active-pauses channel. Pause is unavailable when
notifications are blocked, or when that app has used both daily allowances.
Turning notifications off also ends active pauses when the service observes
the change. Breathing interventions work without notification permission.

### Redirect buttons are missing

The redirect row is optional and appears only with four valid destinations.
Choose four apps in Defaults, or in that app's Custom redirects, and save. An
app selected as a target cannot also be a redirect.

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
the version and Accessibility connection after updating. There is no in-app
automatic updater.

## Stop or remove Speedbreaker

Turn off **Speedbreaker attention pauses** in Android Accessibility settings.
That stops intervention and keeps your settings. You can uninstall Speedbreaker
normally from app info; uninstalling removes its local app data. There is no
cloud copy to restore from.

For a problem not covered here, [open a bug report](https://github.com/beejsbj/speedbreaker/issues/new/choose).
Include only the information needed to reproduce it, with private details removed.
