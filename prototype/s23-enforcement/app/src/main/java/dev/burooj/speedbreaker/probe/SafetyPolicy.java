package dev.burooj.speedbreaker.probe;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.telecom.TelecomManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SafetyPolicy {
    private final Set<String> protectedPackages = new HashSet<>();
    private final Set<String> emergencyYieldPackages = new HashSet<>();

    SafetyPolicy(Context context) {
        PackageManager pm = context.getPackageManager();
        protectedPackages.add(context.getPackageName());
        protectedPackages.add("android");
        protectedPackages.add("com.android.systemui");
        protectedPackages.add("com.android.settings");
        protectedPackages.add("com.android.permissioncontroller");
        protectedPackages.add("com.google.android.permissioncontroller");
        protectedPackages.add("com.android.packageinstaller");
        protectedPackages.add("com.google.android.packageinstaller");
        emergencyYieldPackages.add("com.android.incallui");
        emergencyYieldPackages.add("com.samsung.android.incallui");
        emergencyYieldPackages.add("com.google.android.dialer");
        emergencyYieldPackages.add("com.samsung.android.dialer");
        emergencyYieldPackages.add("com.android.emergency");
        emergencyYieldPackages.add("com.google.android.emergency");

        TelecomManager telecom = context.getSystemService(TelecomManager.class);
        if (telecom != null) {
            String dialer = telecom.getDefaultDialerPackage();
            if (dialer != null) {
                protectedPackages.add(dialer);
                emergencyYieldPackages.add(dialer);
            }
        }

        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL);
        for (ResolveInfo home : homes) {
            protectedPackages.add(home.activityInfo.packageName);
        }

        InputMethodManager imm = context.getSystemService(InputMethodManager.class);
        if (imm != null) {
            for (InputMethodInfo ime : imm.getEnabledInputMethodList()) {
                protectedPackages.add(ime.getPackageName());
            }
        }
    }

    boolean isProtected(String packageName) {
        return packageName == null || protectedPackages.contains(packageName);
    }

    boolean shouldYieldOverlay(String packageName) {
        return packageName != null && emergencyYieldPackages.contains(packageName);
    }
}
