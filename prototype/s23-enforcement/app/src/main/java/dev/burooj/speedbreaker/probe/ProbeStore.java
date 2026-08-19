package dev.burooj.speedbreaker.probe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

final class ProbeStore {
    private static final String FILE = "speedbreaker_probe";
    private final SharedPreferences prefs;

    ProbeStore(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        normalizeForBoot(context);
    }

    String targetPackage() {
        return prefs.getString("target_package", "com.google.android.youtube");
    }

    void setTargetPackage(String value) {
        prefs.edit().putString("target_package", value).apply();
    }

    int thresholdSeconds() {
        return Math.max(3, prefs.getInt("threshold_seconds", 10));
    }

    void setThresholdSeconds(int value) {
        prefs.edit().putInt("threshold_seconds", Math.max(3, value)).apply();
    }

    int cooldownSeconds() {
        return Math.max(3, prefs.getInt("cooldown_seconds", 15));
    }

    void setCooldownSeconds(int value) {
        prefs.edit().putInt("cooldown_seconds", Math.max(3, value)).apply();
    }

    long allowedUntilElapsed() {
        long stored = prefs.getLong("allowed_until_elapsed", 0L);
        if (stored > SystemClock.elapsedRealtime() + 24L * 60L * 60L * 1000L) {
            prefs.edit().remove("allowed_until_elapsed").apply();
            return 0L;
        }
        return stored;
    }

    void grantCooldown() {
        prefs.edit()
                .putLong("allowed_until_elapsed", SystemClock.elapsedRealtime() + cooldownSeconds() * 1000L)
                .apply();
    }

    boolean isCoolingDown() {
        return SystemClock.elapsedRealtime() < allowedUntilElapsed();
    }

    long breakerDeadlineElapsed() {
        return prefs.getLong("breaker_deadline_elapsed", 0L);
    }

    void beginBreaker(long deadlineElapsed, String reason) {
        prefs.edit()
                .putBoolean("breaker_active", true)
                .putLong("breaker_deadline_elapsed", deadlineElapsed)
                .putString("breaker_reason", reason)
                .putInt("breaker_count", prefs.getInt("breaker_count", 0) + 1)
                .apply();
        record("Breaker launched: " + reason);
    }

    boolean breakerActive() {
        return prefs.getBoolean("breaker_active", false);
    }

    void finishBreaker(String outcome) {
        prefs.edit()
                .putBoolean("breaker_active", false)
                .remove("breaker_deadline_elapsed")
                .apply();
        record("Breaker outcome: " + outcome);
    }

    void record(String message) {
        prefs.edit()
                .putString("last_event", message)
                .putLong("last_event_wall", System.currentTimeMillis())
                .apply();
    }

    String lastEvent() {
        return prefs.getString("last_event", "No probe event yet");
    }

    long lastEventWall() {
        return prefs.getLong("last_event_wall", 0L);
    }

    int breakerCount() {
        return prefs.getInt("breaker_count", 0);
    }

    void armActivityExperiment() {
        prefs.edit().putBoolean("activity_experiment_armed", true).apply();
        record("Background Activity experiment armed for next target entry");
    }

    boolean consumeActivityExperiment() {
        boolean armed = prefs.getBoolean("activity_experiment_armed", false);
        if (armed) {
            prefs.edit()
                    .putBoolean("activity_experiment_armed", false)
                    .putBoolean("activity_experiment_visible", false)
                    .apply();
        }
        return armed;
    }

    void setActivityExperimentVisible(boolean visible) {
        prefs.edit().putBoolean("activity_experiment_visible", visible).apply();
    }

    boolean activityExperimentVisible() {
        return prefs.getBoolean("activity_experiment_visible", false);
    }

    void resetRuntimeState() {
        prefs.edit()
                .remove("allowed_until_elapsed")
                .remove("breaker_deadline_elapsed")
                .remove("breaker_active")
                .remove("breaker_count")
                .remove("last_event")
                .remove("last_event_wall")
                .remove("activity_experiment_armed")
                .remove("activity_experiment_visible")
                .apply();
    }

    private void normalizeForBoot(Context context) {
        int currentBoot = Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.BOOT_COUNT,
                -1);
        int storedBoot = prefs.getInt("boot_count", Integer.MIN_VALUE);
        if (currentBoot == storedBoot) {
            return;
        }
        prefs.edit()
                .putInt("boot_count", currentBoot)
                .remove("allowed_until_elapsed")
                .remove("breaker_deadline_elapsed")
                .remove("breaker_active")
                .remove("activity_experiment_armed")
                .remove("activity_experiment_visible")
                .putString("last_event", "New boot detected; transient deadlines cleared")
                .putLong("last_event_wall", System.currentTimeMillis())
                .commit();
    }
}
