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

## Verified implementation choice

`TYPE_ACCESSIBILITY_OVERLAY` is viable on this S23+ and is the baseline
enforcement primitive. It works without the separate Draw over other apps
permission and does not depend on Android allowing a background Activity start.

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

- Screen-off/on while a target remains underneath.
- Picture-in-picture and split-screen visibility accounting.
- Incoming-call/emergency yield.
- Accessibility service reconnection after a real process death and reboot.
- Samsung battery optimization / idle survival.

`am kill dev.burooj.speedbreaker.probe` did not terminate the bound service
process, so that command did not prove the rebind case.

## Probe defect found and closed

The first Activity comparison crashed on Android 16 because
`getInsetsController()` was called before `setContentView()` had attached the
decor view. The exact on-device launch was used as the regression loop. Moving
the insets call after content attachment changed the loop from a deterministic
`PhoneWindow.getInsetsController()` null dereference to a focused
`BreakerActivity` with no fatal exception. The same fixed Activity also passed
the stricter true-background launch described above.
