package dev.burooj.speedbreaker.probe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(20, 20, 20);
    private static final int IVORY = Color.rgb(247, 243, 234);

    private ProbeStore store;
    private Spinner appSpinner;
    private EditText thresholdInput;
    private EditText cooldownInput;
    private TextView status;
    private List<AppChoice> choices = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ProbeStore(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(IVORY);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(32), dp(24), dp(40));
        scroll.addView(content);

        TextView title = text("Speedbreaker\nS23+ enforcement probe", 30);
        title.setLetterSpacing(-0.02f);
        content.addView(title);
        content.addView(spacer(18));

        TextView note = text("Throwaway hardware proof. Select one launchable app, enable the service, then open that app.", 16);
        note.setAlpha(0.72f);
        content.addView(note);
        content.addView(spacer(24));

        choices = loadLaunchableApps();
        appSpinner = new Spinner(this);
        ArrayAdapter<AppChoice> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appSpinner.setAdapter(adapter);
        selectConfiguredTarget();
        content.addView(label("Target app"));
        content.addView(appSpinner, fullWidth(dp(52)));
        content.addView(spacer(16));

        thresholdInput = numberInput(store.thresholdSeconds());
        content.addView(label("Continuous-use threshold (seconds)"));
        content.addView(thresholdInput, fullWidth(dp(52)));
        content.addView(spacer(12));

        cooldownInput = numberInput(store.cooldownSeconds());
        content.addView(label("Cooldown after Continue (seconds)"));
        content.addView(cooldownInput, fullWidth(dp(52)));
        content.addView(spacer(16));

        content.addView(button("Save probe settings", view -> saveSettings()));
        content.addView(button("Enable Accessibility service", view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        content.addView(button("Open Usage Access settings", view -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        content.addView(button("Open selected app", view -> openSelectedApp()));
        content.addView(button("Arm one background-Activity experiment", view -> {
            saveSettings();
            store.armActivityExperiment();
            refreshStatus();
            Toast.makeText(this, "Next target entry will try the Activity path once", Toast.LENGTH_LONG).show();
        }));
        content.addView(button("Preview breaker", view -> {
            saveSettings();
            Intent intent = new Intent(this, BreakerActivity.class);
            intent.putExtra(BreakerActivity.EXTRA_TARGET_PACKAGE, store.targetPackage());
            intent.putExtra(BreakerActivity.EXTRA_REASON, "manual preview");
            startActivity(intent);
        }));
        content.addView(button("Reset probe state", view -> {
            store.resetRuntimeState();
            refreshStatus();
        }));

        content.addView(spacer(24));
        status = text("", 14);
        status.setTypeface(android.graphics.Typeface.MONOSPACE);
        status.setTextIsSelectable(true);
        content.addView(status);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) {
            refreshStatus();
        }
    }

    private void saveSettings() {
        if (choices.isEmpty()) {
            Toast.makeText(this, "No safe launchable apps found", Toast.LENGTH_SHORT).show();
            return;
        }
        AppChoice choice = (AppChoice) appSpinner.getSelectedItem();
        if (new SafetyPolicy(this).isProtected(choice.packageName)) {
            Toast.makeText(this, "That package is safety-excluded", Toast.LENGTH_LONG).show();
            return;
        }
        store.setTargetPackage(choice.packageName);
        store.setThresholdSeconds(parseSeconds(thresholdInput, 10));
        store.setCooldownSeconds(parseSeconds(cooldownInput, 15));
        store.record("Settings saved for " + choice.packageName);
        refreshStatus();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    private void openSelectedApp() {
        saveSettings();
        Intent launch = getPackageManager().getLaunchIntentForPackage(store.targetPackage());
        if (launch == null) {
            Toast.makeText(this, "No launcher intent for selected app", Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(launch);
    }

    private void refreshStatus() {
        long wall = store.lastEventWall();
        String eventTime = wall == 0L ? "never" : DateFormat.getDateTimeInstance().format(new Date(wall));
        status.setText(
                "Accessibility: " + (isAccessibilityEnabled() ? "ENABLED" : "disabled") +
                "\nUsage access: " + (hasUsageAccess() ? "ENABLED" : "not granted") +
                "\nTarget: " + store.targetPackage() +
                "\nThreshold: " + store.thresholdSeconds() + "s" +
                "\nCooldown: " + store.cooldownSeconds() + "s" +
                "\nBreaker count: " + store.breakerCount() +
                "\nLast event: " + store.lastEvent() +
                "\nEvent time: " + eventTime
        );
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        if (manager == null) {
            return false;
        }
        ComponentName expected = new ComponentName(this, SpeedbreakerAccessibilityService.class);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (expected.equals(ComponentName.unflattenFromString(info.getId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = getSystemService(AppOpsManager.class);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private List<AppChoice> loadLaunchableApps() {
        SafetyPolicy safety = new SafetyPolicy(this);
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppChoice> result = new ArrayList<>();
        for (ResolveInfo resolved : pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)) {
            String packageName = resolved.activityInfo.packageName;
            if (safety.isProtected(packageName)) {
                continue;
            }
            ApplicationInfo applicationInfo = resolved.activityInfo.applicationInfo;
            result.add(new AppChoice(applicationInfo.loadLabel(pm).toString(), packageName));
        }
        Collections.sort(result, (left, right) -> left.label.compareToIgnoreCase(right.label));
        return result;
    }

    private void selectConfiguredTarget() {
        String configured = store.targetPackage();
        int preferred = -1;
        String[] preferredPackages = {
                configured,
                "com.google.android.youtube",
                "com.instagram.android",
                "com.reddit.frontpage",
                "com.zhiliaoapp.musically"
        };
        for (String preferredPackage : preferredPackages) {
            for (int i = 0; i < choices.size(); i++) {
                if (choices.get(i).packageName.equals(preferredPackage)) {
                    preferred = i;
                    break;
                }
            }
            if (preferred >= 0) {
                break;
            }
        }
        if (preferred >= 0) {
            appSpinner.setSelection(preferred);
        }
    }

    private int parseSeconds(EditText input, int fallback) {
        try {
            return Math.max(3, Integer.parseInt(input.getText().toString()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private TextView label(String value) {
        TextView label = text(value, 13);
        label.setAlpha(0.68f);
        label.setPadding(0, 0, 0, dp(6));
        return label;
    }

    private TextView text(String value, int sizeSp) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(INK);
        text.setTextSize(sizeSp);
        return text;
    }

    private EditText numberInput(int value) {
        EditText input = new EditText(this);
        input.setText(String.valueOf(value));
        input.setTextColor(INK);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = fullWidth(dp(52));
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private View spacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return spacer;
    }

    private LinearLayout.LayoutParams fullWidth(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class AppChoice {
        final String label;
        final String packageName;

        AppChoice(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }

        @Override
        public String toString() {
            return label + "\n" + packageName;
        }
    }
}
