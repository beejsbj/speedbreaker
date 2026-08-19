package dev.burooj.speedbreaker.probe;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public final class SpeedbreakerAccessibilityService extends AccessibilityService {
    private static final String TAG = "SpeedbreakerProbe";
    private static final long TICK_MS = 500L;
    private static final long BREAKER_MS = 5_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProbeStore store;
    private SafetyPolicy safety;
    private BreakerOverlay overlay;
    private boolean targetWasVisible;
    private long lastTickElapsed;
    private long activeUseMs;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tickActiveUse();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        store = new ProbeStore(this);
        safety = new SafetyPolicy(this);
        overlay = new BreakerOverlay(this, store);
        if (store.breakerActive() && store.breakerDeadlineElapsed() > SystemClock.elapsedRealtime()) {
            overlay.show("restored after service reconnect");
        } else {
            store.finishBreaker("service connected; stale breaker state cleared");
            store.record("Accessibility service connected");
        }
        lastTickElapsed = SystemClock.elapsedRealtime();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        handler.postDelayed(() -> reconcileTargetVisibility("service connection"), 250L);
        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (store == null || event == null || event.getPackageName() == null) {
            return;
        }
        String packageName = event.getPackageName().toString();
        if (overlay != null && overlay.isShowing() && safety.shouldYieldOverlay(packageName)) {
            overlay.dismiss();
            store.finishBreaker("yielded to safety UI: " + packageName);
            targetWasVisible = false;
            return;
        }
        reconcileTargetVisibility(eventType(event.getEventType()));
    }

    @Override
    public void onInterrupt() {
        if (store != null) {
            store.record("Accessibility service interrupted");
        }
        Log.w(TAG, "Accessibility service interrupted");
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (overlay != null) {
            overlay.dismiss();
        }
        super.onDestroy();
    }

    private void tickActiveUse() {
        if (store == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long delta = Math.min(2_000L, Math.max(0L, now - lastTickElapsed));
        lastTickElapsed = now;

        String target = store.targetPackage();
        if (safety.isProtected(target)) {
            activeUseMs = 0L;
            return;
        }

        PowerManager power = getSystemService(PowerManager.class);
        boolean interactive = power != null && power.isInteractive();
        boolean targetVisible = interactive && readTargetWindowVisibility(target);

        if (!targetVisible) {
            activeUseMs = 0L;
            return;
        }
        if (store.breakerActive() || store.isCoolingDown()) {
            return;
        }

        activeUseMs += delta;
        if (activeUseMs >= store.thresholdSeconds() * 1000L) {
            activeUseMs = 0L;
            launchBreaker("continuous visible use");
        }
    }

    private boolean readTargetWindowVisibility(String packageName) {
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows.isEmpty()) {
            return targetWasVisible;
        }
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null || root.getPackageName() == null) {
                continue;
            }
            if (packageName.contentEquals(root.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private void reconcileTargetVisibility(String source) {
        String target = store.targetPackage();
        boolean targetVisible = readTargetWindowVisibility(target);
        boolean enteringTarget = targetVisible && !targetWasVisible;
        targetWasVisible = targetVisible;
        if (enteringTarget && !store.breakerActive() && !store.isCoolingDown()) {
            launchBreaker("app entry: " + source);
        }
    }

    private void launchBreaker(String reason) {
        String target = store.targetPackage();
        if (safety.isProtected(target) || store.breakerActive()) {
            return;
        }

        store.beginBreaker(SystemClock.elapsedRealtime() + BREAKER_MS, reason);
        if (!store.consumeActivityExperiment()) {
            overlay.show(reason);
            Log.i(TAG, "Breaker accessibility overlay shown: " + reason);
            return;
        }

        Intent intent = new Intent(this, BreakerActivity.class)
                .putExtra(BreakerActivity.EXTRA_TARGET_PACKAGE, target)
                .putExtra(BreakerActivity.EXTRA_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(intent);
            Log.i(TAG, "Breaker activity requested: " + reason);
            handler.postDelayed(() -> {
                if (store.breakerActive() && !store.activityExperimentVisible()) {
                    store.record("Background Activity did not become visible; overlay fallback shown");
                    overlay.show("Activity path blocked; overlay fallback");
                }
            }, 2_000L);
        } catch (RuntimeException error) {
            store.record("Activity launch failed; overlay fallback: " + error.getClass().getSimpleName());
            overlay.show("Activity launch failed; overlay fallback");
            Log.e(TAG, "Unable to launch breaker activity", error);
        }
    }

    void continueFromOverlay() {
        activeUseMs = 0L;
        store.grantCooldown();
        store.finishBreaker("continue with cooldown from overlay");
        overlay.dismiss();
    }

    void leaveFromOverlay() {
        activeUseMs = 0L;
        store.finishBreaker("leave to Home from overlay");
        overlay.dismiss();
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private String eventType(int eventType) {
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return "window state";
        }
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return "windows changed";
        }
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return "window content";
        }
        return String.valueOf(eventType);
    }
}
