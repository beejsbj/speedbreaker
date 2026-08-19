package dev.burooj.speedbreaker.probe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class BreakerActivity extends Activity {
    static final String EXTRA_TARGET_PACKAGE = "target_package";
    static final String EXTRA_REASON = "reason";

    private static final int INK = Color.rgb(20, 20, 20);
    private static final int IVORY = Color.rgb(247, 243, 234);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProbeStore store;
    private TextView countdown;
    private Button continueButton;
    private String targetPackage;
    private boolean completed;

    private final Runnable update = new Runnable() {
        @Override
        public void run() {
            long remaining = Math.max(0L, store.breakerDeadlineElapsed() - SystemClock.elapsedRealtime());
            if (remaining > 0L) {
                countdown.setText("Breathe\n" + ((remaining + 999L) / 1000L));
                handler.postDelayed(this, 100L);
            } else {
                countdown.setText("Notice the choice.");
                continueButton.setEnabled(true);
                continueButton.setAlpha(1f);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ProbeStore(this);
        store.setActivityExperimentVisible(true);
        targetPackage = getIntent().getStringExtra(EXTRA_TARGET_PACKAGE);
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        if (targetPackage == null) {
            targetPackage = store.targetPackage();
        }
        if (!store.breakerActive() || store.breakerDeadlineElapsed() <= 0L) {
            store.beginBreaker(SystemClock.elapsedRealtime() + 5_000L, reason == null ? "manual" : reason);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(IVORY);

        TextView paused = text("PAUSED", 13);
        paused.setLetterSpacing(0.22f);
        paused.setAlpha(0.55f);
        root.addView(paused);

        BreathRing ring = new BreathRing(this);
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(210), dp(210));
        ringParams.topMargin = dp(28);
        ringParams.bottomMargin = dp(20);
        root.addView(ring, ringParams);

        countdown = text("Breathe", 30);
        countdown.setGravity(Gravity.CENTER);
        root.addView(countdown);

        TextView reasonText = text(reason == null ? "" : reason, 13);
        reasonText.setAlpha(0.48f);
        reasonText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        reasonParams.topMargin = dp(14);
        root.addView(reasonText, reasonParams);

        continueButton = button("Continue", view -> continueToTarget());
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.28f);
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        continueParams.topMargin = dp(36);
        root.addView(continueButton, continueParams);

        Button leaveButton = button("Leave", view -> leaveTarget());
        LinearLayout.LayoutParams leaveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        leaveParams.topMargin = dp(8);
        root.addView(leaveButton, leaveParams);

        setContentView(root);
        getWindow().setStatusBarColor(IVORY);
        getWindow().setNavigationBarColor(IVORY);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        handler.post(update);
    }

    @Override
    public void onBackPressed() {
        store.record("Back blocked during breaker");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!completed && !isChangingConfigurations()) {
            store.setActivityExperimentVisible(false);
            store.finishBreaker("Activity path left through system UI");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(update);
        store.setActivityExperimentVisible(false);
        super.onDestroy();
    }

    private void continueToTarget() {
        completed = true;
        store.grantCooldown();
        store.finishBreaker("continue with cooldown");
        Intent launch = getPackageManager().getLaunchIntentForPackage(targetPackage);
        finish();
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
        }
    }

    private void leaveTarget() {
        completed = true;
        store.finishBreaker("leave to Home");
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        finish();
        startActivity(home);
    }

    private TextView text(String value, int sizeSp) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(INK);
        text.setTextSize(sizeSp);
        return text;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class BreathRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long started = SystemClock.elapsedRealtime();

        BreathRing(Activity context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
            paint.setColor(INK);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float phase = ((SystemClock.elapsedRealtime() - started) % 8_000L) / 8_000f;
            float wave = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
            float radius = Math.min(getWidth(), getHeight()) * (0.31f + 0.12f * wave);
            paint.setAlpha(120 + Math.round(100f * wave));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, paint);
            postInvalidateOnAnimation();
        }
    }
}
