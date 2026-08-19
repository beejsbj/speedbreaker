package dev.burooj.speedbreaker.probe;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BreakerOverlay {
    private static final int INK = Color.rgb(20, 20, 20);
    private static final int IVORY = Color.rgb(247, 243, 234);

    private final SpeedbreakerAccessibilityService service;
    private final ProbeStore store;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout root;
    private TextView countdown;
    private Button continueButton;

    private final Runnable update = new Runnable() {
        @Override
        public void run() {
            if (root == null) {
                return;
            }
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

    BreakerOverlay(SpeedbreakerAccessibilityService service, ProbeStore store) {
        this.service = service;
        this.store = store;
        this.windowManager = service.getSystemService(WindowManager.class);
    }

    void show(String reason) {
        if (root != null || windowManager == null) {
            return;
        }

        root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(IVORY);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    store.record("Back blocked by accessibility overlay");
                }
                return true;
            }
            return false;
        });

        TextView paused = text("PAUSED", 13);
        paused.setLetterSpacing(0.22f);
        paused.setAlpha(0.55f);
        root.addView(paused);

        BreathRing ring = new BreathRing();
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(dp(210), dp(210));
        ringParams.topMargin = dp(28);
        ringParams.bottomMargin = dp(20);
        root.addView(ring, ringParams);

        countdown = text("Breathe", 30);
        countdown.setGravity(Gravity.CENTER);
        root.addView(countdown);

        TextView reasonText = text(reason, 13);
        reasonText.setAlpha(0.48f);
        reasonText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        reasonParams.topMargin = dp(14);
        root.addView(reasonText, reasonParams);

        continueButton = button("Continue", view -> service.continueFromOverlay());
        continueButton.setEnabled(false);
        continueButton.setAlpha(0.28f);
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        continueParams.topMargin = dp(36);
        root.addView(continueButton, continueParams);

        Button leaveButton = button("Leave", view -> service.leaveFromOverlay());
        LinearLayout.LayoutParams leaveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        leaveParams.topMargin = dp(8);
        root.addView(leaveButton, leaveParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("Speedbreaker enforcement probe");

        try {
            windowManager.addView(root, params);
            root.requestFocus();
            handler.post(update);
        } catch (RuntimeException error) {
            root = null;
            store.finishBreaker("overlay failed: " + error.getClass().getSimpleName());
        }
    }

    void dismiss() {
        handler.removeCallbacks(update);
        if (root == null || windowManager == null) {
            return;
        }
        try {
            windowManager.removeView(root);
        } catch (RuntimeException ignored) {
            // The system already detached the accessibility window.
        } finally {
            root = null;
            countdown = null;
            continueButton = null;
        }
    }

    boolean isShowing() {
        return root != null;
    }

    private TextView text(String value, int sizeSp) {
        TextView text = new TextView(service);
        text.setText(value);
        text.setTextColor(INK);
        text.setTextSize(sizeSp);
        return text;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(service);
        button.setText(label);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private final class BreathRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long started = SystemClock.elapsedRealtime();

        BreathRing() {
            super(service);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * service.getResources().getDisplayMetrics().density);
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
