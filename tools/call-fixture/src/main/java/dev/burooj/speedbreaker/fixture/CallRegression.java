package dev.burooj.speedbreaker.fixture;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Native instrumentation in the fixture APK: no production seams or test libraries. */
public final class CallRegression extends Instrumentation {
    private static final String PRODUCT = "dev.burooj.speedbreaker";
    private static final String FIXTURE = "dev.burooj.speedbreaker.fixture";
    private static final long HARD_LOCK_MS = 8_000;
    private UiAutomation automation;
    private TelecomManager telecom;
    private TelephonyManager telephony;
    private AudioManager audio;
    private PrintWriter trace;
    private String scenario;
    private long launchedAt;
    private long firstLaunchAt;
    private long readyAt;
    private long yieldedAt;
    private String stage = "warmup";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        scenario = arguments.getString("scenario", "ringing");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int originalRinger = -1;
        try {
            require(scenario.equals("navigation") || scenario.equals("ringing")
                    || scenario.equals("silent"), "Unknown scenario");
            trace = new PrintWriter(new File(getTargetContext().getFilesDir(), "trace.jsonl"));
            // The default UiAutomation flags disable the service under test!
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            telecom = getTargetContext().getSystemService(TelecomManager.class);
            telephony = getTargetContext().getSystemService(TelephonyManager.class);
            audio = getTargetContext().getSystemService(AudioManager.class);
            require(telecom != null && telephony != null && audio != null, "Missing phone services");
            require(!telecom.isInCall(), "Emulator already has a call");
            originalRinger = audio.getRingerMode();
            // Silent mode can enable DND. Permit the call's visual controls explicitly;
            // otherwise a missing heads-up would test Android's DND policy, not Speedbreaker.
            NotificationManager notifications = getTargetContext().getSystemService(NotificationManager.class);
            notifications.setNotificationPolicy(new NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY, 0));
            audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            warmUp();
            audio.setRingerMode(scenario.equals("silent")
                    ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL);
            if (scenario.equals("silent")) {
                require(audio.getRingerMode() == AudioManager.RINGER_MODE_SILENT,
                        "Silent scenario did not enter silent mode");
            }
            stage = "test";
            firstLaunchAt = 0;
            launchTarget();
            awaitOverlay(true, launchedAt + 4_000);
            screenshot("locked.png");
            if (scenario.equals("navigation")) testNavigation();
            else testCall();
            result.putString("result", "PASS");
            result.putString("scenario", scenario);
            result.putLong("launch_elapsed_ms", firstLaunchAt);
            if (yieldedAt != 0) {
                result.putLong("yield_after_launch_ms", yieldedAt - firstLaunchAt);
                result.putLong("yield_after_host_ready_ms", yieldedAt - readyAt);
            }
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
            if (trace != null) failure.printStackTrace(trace);
            try { screenshot("failure.png"); } catch (Throwable ignored) { }
        } finally {
            if (audio != null && originalRinger >= 0) {
                try { audio.setRingerMode(originalRinger); } catch (RuntimeException ignored) { }
            }
            if (trace != null) trace.close();
        }
        finish(Activity.RESULT_OK, result); // Host checks explicit PASS, not am's process exit code.
    }

    private void warmUp() throws Exception {
        // A fresh CI AVD has cold Compose, ART and dialer initialization. Warm those outside
        // the timed assertion, then require a genuinely new target entry for the actual test.
        launchTarget();
        awaitOverlay(true, SystemClock.elapsedRealtime() + 25_000);
        sendKey(KeyEvent.KEYCODE_HOME);
        awaitOverlay(false, SystemClock.elapsedRealtime() + 15_000);
        if (!scenario.equals("navigation")) {
            phase("SB_WARM_CALL_READY");
            long deadline = SystemClock.elapsedRealtime() + 25_000;
            boolean controls = false;
            while (SystemClock.elapsedRealtime() < deadline) {
                Snapshot state = snapshot();
                if (state.inCall && state.callState == TelephonyManager.CALL_STATE_RINGING
                        && state.answer != null) {
                    controls = true;
                    break;
                }
                SystemClock.sleep(100);
            }
            require(controls, "Emulator warmup never produced SystemUI incoming-call controls");
            phase("SB_WARM_CANCEL_READY");
            deadline = SystemClock.elapsedRealtime() + 10_000;
            while (telecom.isInCall() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            require(!telecom.isInCall(), "Warmup call did not end");
        }
        sendKey(KeyEvent.KEYCODE_HOME);
        SystemClock.sleep(1_500);
        require(!snapshot().overlay, "Warmup did not leave target session");
    }

    private void launchTarget() {
        Intent target = new Intent(getTargetContext(), TargetActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        launchedAt = SystemClock.elapsedRealtime();
        if (firstLaunchAt == 0) firstLaunchAt = launchedAt;
        runOnMainSync(() -> getTargetContext().startActivity(target));
    }

    private void testNavigation() throws Exception {
        sendKey(KeyEvent.KEYCODE_HOME);
        holdOverlayUntil(launchedAt + 3_000);
        sendKey(KeyEvent.KEYCODE_APP_SWITCH);
        holdOverlayUntil(launchedAt + 7_500);
        awaitOverlay(false, launchedAt + 10_000);
        screenshot("navigation-released.png");
    }

    private void testCall() throws Exception {
        // Start strictly inside a fresh hard lock. This timestamp precedes the real overlay's
        // creation, so a yield <8s from launch is a conservative proof of the lock boundary.
        require(SystemClock.elapsedRealtime() - launchedAt < 5_000,
                "Fixture setup consumed the hard-lock timing budget");
        require(snapshot().overlay, "Breaker disappeared before call injection");
        readyAt = SystemClock.elapsedRealtime();
        phase("SB_CALL_READY"); // Host now sends adb emu gsm call; no ACTION_CALL or real network.
        long deadline = launchedAt + HARD_LOCK_MS - 250;
        boolean sawRinging = false;
        Snapshot accessible = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            Snapshot state = snapshot();
            if (state.callState == TelephonyManager.CALL_STATE_RINGING && state.inCall) {
                sawRinging = true;
                // A dialer activity would exercise the already-working package safety path.
                require(state.apps.stream().allMatch(FIXTURE::equals),
                        "Call opened another application window; not the SystemUI regression: " + state.apps);
                if (!state.overlay && state.apps.contains(FIXTURE)
                        && state.answer != null && state.decline) {
                    yieldedAt = SystemClock.elapsedRealtime();
                    accessible = state;
                    break;
                }
            }
            SystemClock.sleep(40);
        }
        require(sawRinging, "No real Telecom RINGING state before the hard-lock deadline");
        require(accessible != null, "No unobstructed SystemUI Answer/Decline before eight seconds");
        require(yieldedAt - firstLaunchAt < HARD_LOCK_MS,
                "Window sampling completed after the eight-second hard lock");
        screenshot("systemui-incoming.png");
        // Keep ringing briefly: a one-shot yield followed by re-entry must fail.
        long holdUntil = SystemClock.elapsedRealtime() + 700;
        while (SystemClock.elapsedRealtime() < holdUntil) {
            Snapshot state = snapshot();
            require(state.inCall && state.callState == TelephonyManager.CALL_STATE_RINGING,
                    "Call unexpectedly stopped ringing");
            require(!state.overlay, "Breaker re-entered while ringing");
            SystemClock.sleep(40);
        }
        Snapshot beforeTap = snapshot();
        require(beforeTap.answer != null, "SystemUI Answer disappeared before touch");
        // A real coordinate touch, not ACTION_CLICK (which could bypass an obscuring window).
        tap(beforeTap.answer);
        long answerDeadline = SystemClock.elapsedRealtime() + 5_000;
        boolean answered = false;
        while (SystemClock.elapsedRealtime() < answerDeadline) {
            Snapshot state = snapshot();
            require(!state.overlay, "Breaker re-entered while answering");
            if (state.inCall && state.callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                answered = true;
                break;
            }
            SystemClock.sleep(40);
        }
        require(answered, "Coordinate touch did not answer the simulated incoming call");
        screenshot("answered.png");
        phase("SB_CANCEL_READY");
        long cancelDeadline = SystemClock.elapsedRealtime() + 5_000;
        while (telecom.isInCall() && SystemClock.elapsedRealtime() < cancelDeadline) {
            require(!snapshot().overlay, "Breaker re-entered during active call");
            SystemClock.sleep(40);
        }
        require(!telecom.isInCall(), "Host did not end simulated call");
        // Return explicitly; the dialer is allowed to remain visible after hangup.
        sendKey(KeyEvent.KEYCODE_HOME);
        SystemClock.sleep(1_000);
        launchTarget();
        awaitOverlay(true, SystemClock.elapsedRealtime() + 5_000);
        screenshot("reentry.png");
    }

    private void holdOverlayUntil(long until) throws Exception {
        while (SystemClock.elapsedRealtime() < until) {
            require(snapshot().overlay, "Home/Recents escaped before eight seconds");
            SystemClock.sleep(40);
        }
    }

    private void awaitOverlay(boolean expected, long deadline) throws Exception {
        while (SystemClock.elapsedRealtime() < deadline) {
            if (snapshot().overlay == expected) return;
            SystemClock.sleep(40);
        }
        throw new AssertionError("Timed out waiting for overlay=" + expected);
    }

    @SuppressWarnings("deprecation")
    private Snapshot snapshot() throws Exception {
        Snapshot result = new Snapshot();
        result.inCall = telecom.isInCall();
        result.callState = telephony.getCallState();
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            String owner = root == null || root.getPackageName() == null
                    ? "" : root.getPackageName().toString();
            if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                    && (PRODUCT.equals(owner) || "Speedbreaker".contentEquals(
                            window.getTitle() == null ? "" : window.getTitle()))) result.overlay = true;
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && !owner.isEmpty()) {
                result.apps.add(owner);
            }
            if ("com.android.systemui".equals(owner) && root != null) {
                findCallControls(root, result);
            }
            if (root != null) root.recycle();
            window.recycle();
        }
        trace.println(new JSONObject().put("elapsed_ms", SystemClock.elapsedRealtime())
                .put("stage", stage)
                .put("overlay", result.overlay).put("in_call", result.inCall)
                .put("call_state", result.callState).put("audio_mode", audio.getMode())
                .put("ringer_mode", audio.getRingerMode()).put("apps", result.apps.toString())
                .put("systemui_answer", result.answer != null).put("systemui_decline", result.decline));
        trace.flush();
        return result;
    }

    @SuppressWarnings("deprecation")
    private void findCallControls(AccessibilityNodeInfo node, Snapshot state) {
        String text = String.valueOf(node.getText()).toLowerCase(Locale.ROOT).trim();
        String description = String.valueOf(node.getContentDescription()).toLowerCase(Locale.ROOT).trim();
        if (node.isVisibleToUser() && node.isEnabled()) {
            if (isAction(text, "answer") || isAction(description, "answer")
                    || isAction(text, "accept") || isAction(description, "accept")) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (!bounds.isEmpty()) state.answer = bounds;
            }
            if (isAction(text, "decline") || isAction(description, "decline")) state.decline = true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                findCallControls(child, state);
                child.recycle();
            }
        }
    }

    private boolean isAction(String label, String verb) {
        return label.equals(verb) || label.equals(verb + " call");
    }

    private void screenshot(String name) throws Exception {
        Bitmap image = automation.takeScreenshot();
        require(image != null, "Screenshot unavailable");
        try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getFilesDir(), name))) {
            require(image.compress(Bitmap.CompressFormat.PNG, 100, output), "Screenshot encoding failed");
        } finally { image.recycle(); }
    }

    private void sendKey(int code) {
        long now = SystemClock.uptimeMillis();
        require(automation.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true),
                "Key down injection failed");
        require(automation.injectInputEvent(new KeyEvent(now, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, code, 0), true), "Key up injection failed");
    }

    private void tap(Rect bounds) {
        long downAt = SystemClock.uptimeMillis();
        for (int action : new int[] { MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP }) {
            MotionEvent event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { require(automation.injectInputEvent(event, true), "Touch injection failed"); }
            finally { event.recycle(); }
        }
    }

    private void phase(String value) {
        Bundle status = new Bundle();
        status.putString("stream", "\n" + value + "\n");
        sendStatus(0, status);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Snapshot {
        boolean overlay;
        boolean inCall;
        int callState;
        Rect answer;
        boolean decline;
        final Set<String> apps = new HashSet<>();
    }
}
