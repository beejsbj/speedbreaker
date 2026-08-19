# Galaxy S23+ enforcement results

This is the evidence log for the throwaway enforcement probe. It records what
was observed on hardware, not what the Android APIs are merely expected to do.

## Device and installation

- Device: Samsung Galaxy S23+ (`SM-S916W`, device `dm2q`).
- OS: Android 16, API 36.
- Host ADB: platform-tools 37.0.1 over native TLS Wireless Debugging through
  the phone's private Tailscale address.
- Probe: `dev.burooj.speedbreaker.probe`, version `0.1-probe`, target API 35.
- APK installed successfully with `adb install -r` and launched normally.
- Burooj explicitly enabled **Speedbreaker enforcement probe** in Samsung's
  Accessibility settings. Android reported the service as enabled and bound
  with interactive-window retrieval.

## Verified behavior

### Opening intervention

- YouTube (`com.google.android.youtube`) was the configured target.
- Launching YouTube produced one `TYPE_ACCESSIBILITY_OVERLAY` Speedbreaker.
- Window state while blocked showed the overlay focused and YouTube as the
  focused underlying app.
- The app-content region was opaque and touch-blocking. Android's status bar
  remained above the overlay.
- Pressing Home changed the underlying focused app to Niagara Launcher while
  the Speedbreaker overlay remained the focused window.
- Pressing Recents changed the underlying focused app to Samsung's Recents
  activity while the overlay remained focused.
- Back did not dismiss the overlay. Explicit **Leave** removed it and returned
  to Niagara Launcher.

### Continue and continuous use

- Choosing **Continue** removed the overlay and returned control to YouTube.
- Relaunching YouTube during the configured 15-second cooldown did not create a
  second breaker.
- After cooldown, ten seconds of continuously visible YouTube use produced a
  new breaker with reason `continuous visible use`.

### Leave and repeated entry

- Choosing **Leave** removed the overlay and invoked global Home successfully.
- The focused app became Niagara Launcher (`bitpit.launcher`).
- Reopening YouTube immediately produced a fresh entry breaker.

### Safety exclusions and background time

- Opening Android Settings did not trigger a breaker.
- Opening Speedbreaker Probe itself did not trigger a breaker.
- After Continue, going Home and waiting 28 seconds produced no continuous-use
  breaker. This verifies that background YouTube did not accumulate active time
  in that test.
- After Continue, the screen was turned off with YouTube still underneath. The
  phone remained non-interactive for more than 40 seconds, past both the
  15-second cooldown and 10-second visible-use threshold. The breaker count
  remained unchanged and no breaker became active.
- Waking the phone exposed YouTube again and produced a fresh `app entry`
  breaker. This is consistent with re-intervening on resumed visible use rather
  than charging the screen-off interval toward the continuous-use threshold.

### Accessibility service recovery

- The running probe process was sent `SIGKILL` from its own application UID to
  simulate abrupt process death without force-stopping the package.
- The original PID disappeared, Android started a new process with a different
  PID, and the Accessibility service logged `Accessibility service connected`
  within eight seconds.
- Samsung still reported the service as both enabled and bound after the
  restart. No permission toggle or app relaunch was needed.

### Split-screen active use

- Samsung's native Recents flow created a real split pair with Calculator on
  the left and YouTube on the right. Android reported both tasks visible in
  multi-window mode while Calculator held focus.
- After the setup cooldown expired, ten seconds of visible, unfocused YouTube
  produced a breaker with reason `continuous visible use` while Calculator
  remained the focused app.
- This verifies the probe's window-visibility approach counts a target that is
  visibly present in split-screen; foreground focus alone is not required.
- The framework overlay filled the available application area, excluding the
  landscape system bar. The probe's fixed vertical content did not adapt to the
  short landscape height, however, and clipped **Leave** below the viewport.
  The disposable probe is therefore evidence for enforcement behavior, not a
  reusable production layout.

### Picture-in-picture active use

- YouTube played a test video and entered Android's real pinned
  picture-in-picture mode after Home was pressed.
- Android reported the YouTube task as visible with
  `mLastReportedPictureInPictureMode=true` while Niagara Launcher held focus.
- After cooldown, ten seconds of visible PiP produced a breaker with reason
  `continuous visible use`.
- The test video was then stopped and the media volume was restored to its
  original value.

### Dialer safety yield

- With a YouTube entry breaker visible, opening the system dialer with no
  number immediately dismissed the overlay.
- The recorded outcome was
  `yielded to safety UI: com.samsung.android.dialer`; no call was placed.
- This verifies the configured dialer yield path, but not incoming-call or
  emergency-call behavior.

## Verified implementation choice

The verified baseline for this S23+ is:

1. An owner-enabled `AccessibilityService` receives window changes and retrieves
   interactive windows.
2. Actual application-window visibility, rather than the latest event package
   or focused activity alone, determines target entry and active use. This
   covers foreground, split-screen, and PiP.
3. `PowerManager.isInteractive()` gates accumulation so screen-off time does not
   count.
4. Monotonic elapsed time drives cooldown and visible-use thresholds.
5. A service-owned `TYPE_ACCESSIBILITY_OVERLAY` presents the Speedbreaker and
   survives Home, Recents, and Back until an explicit choice or safety yield.

`TYPE_ACCESSIBILITY_OVERLAY` works without the separate Draw over other apps
permission and does not depend on Android allowing a background Activity start.
The system Accessibility binding restarted the probe after abrupt process death,
so the proof did not require a separate foreground service.

Usage Access was not required for opening triggers or continuous visible-use
accounting in this proof. Its role in reconciling production cumulative-daily
totals remains a separate implementation question.

The comparison Activity is also viable on this device. Android 16 allowed the
Accessibility service to start `BreakerActivity` after Speedbreaker had been
sent Home and YouTube was launched independently. The Activity became focused
without a background-activity-launch denial. It remains a comparison path, not
the baseline, because the service-owned overlay persisted across Home and
Recents in a way an Activity cannot guarantee.

This does **not** make the app literally inescapable. Android system UI remains
above the overlay, and the owner can disable Accessibility, force-stop, or
uninstall the app. The product contract must be persistent friction and
re-intervention, not kiosk/device-owner control.

## Still to exercise

- Incoming-call/emergency yield.
- Accessibility service reconnection after a real device reboot.
- Samsung battery optimization / idle survival.

`am kill dev.burooj.speedbreaker.probe` did not terminate the bound service
process. The later same-UID `SIGKILL` did terminate it and is the process-death
evidence recorded above; it does not substitute for a device reboot.

## Probe defect found and closed

The first Activity comparison crashed on Android 16 because
`getInsetsController()` was called before `setContentView()` had attached the
decor view. The exact on-device launch was used as the regression loop. Moving
the insets call after content attachment changed the loop from a deterministic
`PhoneWindow.getInsetsController()` null dereference to a focused
`BreakerActivity` with no fatal exception. The same fixed Activity also passed
the stricter true-background launch described above.
