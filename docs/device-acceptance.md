# S23+ production acceptance

Authority: BJS-346 interaction contract and BJS-344 daily-use destination. The
August probe proves feasibility, not the behavior of this production APK.
Record the exact commit, APK SHA-256, phone OS, date and observed outcome for
each physical run. A build or unit test does not substitute for a device result.

## Controlled first run

Use Calculator as a harmless temporary target unless Burooj chooses otherwise.
Keep the Accessibility settings route available. Begin with no selected apps;
enable consent and Accessibility through the real onboarding flow, then select
the test target. Do not place an emergency call.

| Scenario | Required observation |
| --- | --- |
| Initial setup | Clear consent, no preselected targets, searchable eligible apps; system safety apps absent. |
| Opening | Target opening presents the production overlay and identifies the app and opening trigger. |
| First eight seconds | Only breathing is actionable; Home/Back/Recents do not dismiss; no premature choice works. |
| Staged unlock | Leave/redirect/Pause become available at eight seconds; Continue waits for the entire breath. |
| Continue | Overlay disappears and target is usable; time starts from the grant. |
| Leave | Home opens, overlay is gone, reopening creates a fresh opening intervention. |
| Redirect | Each configured destination launches and target session ends. Target apps cannot be redirects. |
| Quick return | Absence up to 60 seconds resumes interval without opening; longer absence starts fresh. |
| Continuous use | Configured visible-use interval causes one new intervention. Background time does not count. |
| Multiple windows | Visible target counts in native split-screen and PiP even without focus. No repeated overlay loop. |
| Pause | One token gives 15 minutes immediately; count is per app; notification can end the pause. |
| Expiry/End now | Visible target intervenes immediately; absent target intervenes on next opening. |
| Exhausted pauses | Third pause unavailable; reset time explained. Current day only, no historical counters. |
| Schedules | Outside window is unenforced; activation while target visible intervenes; overnight/per-app replacement works. |
| Post-lock navigation | After eight seconds Home/Back/Recents release immediately and end the target session. |
| Settings/disable escape | Accessibility settings always accessible; disabling removes overlay and app reports inactive. |
| Lock/screen off | Immediate yield; no screen-off time counted; waking follows opening behavior. |
| Dialer | Opening the empty dialer yields immediately. No phone number or call is required. |
| Normal incoming call | A coordinated ordinary incoming call is visible and answerable during the lock. |
| Reboot | After an agreed reboot and unlock, service reconnects; settings/tokens/active pause exact expiry survive. |
| Process recovery | After controlled process termination, service recovers without stale lock or corrupt settings. |
| Samsung idle | Following a substantial idle/overnight period, opening a target still intervenes. |
| Layout/accessibility | Portrait, landscape, split height and large text retain reachable controls; reduced motion respected. |

## Daily-use finish line

After controlled safety/recovery tests pass, select Burooj's real targets and
redirect destinations. Record trial start and intended end in BJS-141. Use for a
full day; check missed/false interventions, inaccessible controls, unexpected
navigation, lost settings/pauses and battery/background survival. Resolve
material failures and repeat affected checks before accepting the production
build. Record Burooj's actual verdict; elapsed time alone is not acceptance.

The app keeps no usage history. Test evidence therefore comes from controlled
observations and Burooj's report, not hidden telemetry or historical counters.
