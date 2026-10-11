package dev.burooj.speedbreaker.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/** An inert selected app. All calls are injected into the emulator modem by the host. */
public final class TargetActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView label = new TextView(this);
        label.setText("SPEEDBREAKER TEST TARGET\n\nEmulator test only. No external calls.");
        label.setGravity(Gravity.CENTER);
        label.setTextSize(22);
        setContentView(label);
    }
}
