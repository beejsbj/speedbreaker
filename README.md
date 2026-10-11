# Speedbreaker

Speedbreaker adds a deliberate breathing pause before you open an app you have
chosen, and optional check-ins during continuous use. It runs locally on Android.

## Install

Share or download `Speedbreaker-1.0.0.apk` and open it on the receiving phone.
Android 8 or newer is required. This release is for sideloading.

1. Install the APK through Android's package installer.
2. Open Speedbreaker and read its Accessibility disclosure.
3. Enable **Speedbreaker attention pauses** in Android Accessibility settings.
4. Choose your target apps. Nothing is selected on a fresh installation.
5. Optionally choose four redirect apps, adjust the breathing duration or
   check-in interval, and set active hours.

On Samsung, if Accessibility shows **Restricted setting**, open
**Settings → Apps → Speedbreaker → More options → Allow restricted settings**,
complete the owner identity check, then enable the service. Keep Play Protect on.
Allow pause notifications to use the fifteen-minute Pause action. On Samsung,
follow the in-app Reliability guidance to allow background operation.

## What a pause does

The default breath lasts twelve seconds. For the first eight seconds, only the
breathing screen is available and ordinary navigation cannot dismiss it.
At eight seconds, Leave, configured redirects, and Pause become available.
Continue becomes available when the full breath finishes.

Each target gets two fifteen-minute pauses per local day. Its notification can
end a pause early. Optional check-ins default to ten minutes of continuous
screen-on use. Returning within sixty seconds resumes that interval; a longer
absence starts a fresh opening pause. Split-screen and picture-in-picture
visibility count as use.

Calls, the dialer, lockscreen, screen-off, and Android Settings take priority.
After eight seconds, ordinary navigation can leave immediately. One Hand
Operation+ edge gestures are unavailable while the breathing overlay is visible;
use Leave, a redirect, or system navigation after the lock.

## Privacy and removal

Accessibility is used to identify visible selected apps and show the pause.
Speedbreaker requests no internet, Usage Access, or separate overlay permission.
It stores settings and the current day's pause state on the device, with no
usage history, analytics, account, or cloud sync. Android backup is disabled.

To stop protection, turn off **Speedbreaker attention pauses** in Accessibility
settings. You can then edit settings or uninstall the app normally. The app
reports when protection is inactive.

## Build and release

Use Java 17 and Android SDK 35. See [build instructions](docs/build-and-test.md),
[release signing](docs/release.md), and the [device test matrix](docs/device-acceptance.md).
The stable application ID is `dev.burooj.speedbreaker`.

The app was developed and physically tested on a Samsung Galaxy S23+. Other
manufacturers' background management and navigation need device-specific
testing. Detailed verification is recorded in `docs/reviews/`.

## Project record

The Linear board is canonical for workflow state; release delivery is recorded
in [BJS-141](https://linear.app/bjs-projects/issue/BJS-141/package-sign-and-install-on-buroojs-phone-with-accessibilityservice).
The original [AI Studio prototype](https://aistudio.google.com/apps/433e02d7-5255-433b-a734-3a046504a9b3)
is retained under `reference/ai-studio/` as historical design evidence.
