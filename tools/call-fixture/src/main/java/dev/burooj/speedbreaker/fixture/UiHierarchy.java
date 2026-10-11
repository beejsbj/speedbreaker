package dev.burooj.speedbreaker.fixture;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/** Persistent, read-only observer: starting/stopping instrumentation kills its target package. */
public final class UiHierarchy extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation automation = getUiAutomation(
                    UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                    | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            File directory = getTargetContext().getFilesDir();
            new File(directory, "ui-ready").createNewFile();
            long deadline = SystemClock.uptimeMillis() + 240_000;
            while (SystemClock.uptimeMillis() < deadline) {
                if (new File(directory, "ui-stop").exists()) break;
                File[] files = directory.listFiles();
                if (files != null) for (File request : files) {
                    if (request.getName().startsWith("ui-jpeg-")) {
                        String suffix = request.getName().substring("ui-jpeg-".length());
                        File temporary = new File(directory, "ui-image-" + suffix + ".tmp");
                        File response = new File(directory, "ui-image-" + suffix + ".jpg");
                        Bitmap screenshot = automation.takeScreenshot();
                        if (screenshot == null) throw new IllegalStateException("UiAutomation screenshot unavailable");
                        try (FileOutputStream stream = new FileOutputStream(temporary)) {
                            if (!screenshot.compress(Bitmap.CompressFormat.JPEG, 95, stream))
                                throw new IllegalStateException("Android JPEG compression failed");
                            stream.flush();
                        } finally {
                            screenshot.recycle();
                        }
                        if (!temporary.renameTo(response))
                            throw new IllegalStateException("Could not publish " + response);
                        request.delete();
                        continue;
                    }
                    if (!request.getName().startsWith("ui-request-")) continue;
                    String suffix = request.getName().substring("ui-request-".length());
                    boolean allWindows = suffix.startsWith("all-");
                    File temporary = new File(directory, "ui-response-" + suffix + ".tmp");
                    File response = new File(directory, "ui-response-" + suffix + ".xml");
                    try (FileOutputStream stream = new FileOutputStream(temporary)) {
                        XmlSerializer xml = Xml.newSerializer();
                        xml.setOutput(stream, "UTF-8");
                        xml.startDocument("UTF-8", true);
                        if (allWindows) {
                            xml.startTag(null, "hierarchy");
                            List<AccessibilityWindowInfo> windows = automation.getWindows();
                            for (AccessibilityWindowInfo window : windows) {
                                xml.startTag(null, "window");
                                attribute(xml, "type", Integer.toString(window.getType()));
                                attribute(xml, "type-name", windowTypeName(window.getType()));
                                attribute(xml, "window-id", Integer.toString(window.getId()));
                                attribute(xml, "display-id", Build.VERSION.SDK_INT >= 30
                                        ? Integer.toString(window.getDisplayId()) : "unavailable-api<30");
                                attribute(xml, "active", Boolean.toString(window.isActive()));
                                attribute(xml, "focused", Boolean.toString(window.isFocused()));
                                attribute(xml, "layer", Integer.toString(window.getLayer()));
                                attribute(xml, "title", window.getTitle());
                                Rect bounds = new Rect();
                                window.getBoundsInScreen(bounds);
                                attribute(xml, "bounds", bounds.toShortString());
                                if (Build.VERSION.SDK_INT >= 30) {
                                    Region touchable = new Region();
                                    window.getRegionInScreen(touchable);
                                    Region clipped = new Region(bounds);
                                    clipped.op(touchable, Region.Op.DIFFERENCE);
                                    attribute(xml, "touchable-region-source", "getRegionInScreen");
                                    attribute(xml, "touchable-region-rects", regionRects(touchable));
                                    attribute(xml, "bounds-minus-touchable-rects", regionRects(clipped));
                                } else {
                                    attribute(xml, "touchable-region-source", "bounds-fallback-api<30");
                                    attribute(xml, "touchable-region-rects", bounds.toShortString());
                                    attribute(xml, "bounds-minus-touchable-rects", "unavailable-api<30");
                                }
                                AccessibilityNodeInfo root = window.getRoot();
                                attribute(xml, "package", root == null ? null : root.getPackageName());
                                if (root != null) writeNode(xml, root);
                                xml.endTag(null, "window");
                            }
                            xml.endTag(null, "hierarchy");
                        } else {
                            AccessibilityNodeInfo root = null;
                            long rootDeadline = SystemClock.uptimeMillis() + 1_000;
                            while (root == null && SystemClock.uptimeMillis() < rootDeadline) {
                                root = automation.getRootInActiveWindow();
                                if (root == null) SystemClock.sleep(50);
                            }
                            if (root == null) {
                                xml.startTag(null, "error");
                                attribute(xml, "message", "No active Accessibility window");
                                xml.endTag(null, "error");
                            } else {
                                xml.startTag(null, "hierarchy");
                                writeNode(xml, root);
                                xml.endTag(null, "hierarchy");
                            }
                        }
                        xml.endDocument();
                        stream.flush();
                    }
                    if (!temporary.renameTo(response)) {
                        throw new IllegalStateException("Could not publish " + response);
                    }
                    request.delete();
                }
                SystemClock.sleep(50);
            }
            if (SystemClock.uptimeMillis() >= deadline) throw new IllegalStateException("Observer timed out");
            result.putString("result", "PASS");
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
        }
        finish(Activity.RESULT_OK, result); // The host checks PASS, not am's exit status.
    }

    private static void writeNode(XmlSerializer xml, AccessibilityNodeInfo node) throws Exception {
        xml.startTag(null, "node");
        attribute(xml, "text", node.getText());
        attribute(xml, "content-desc", node.getContentDescription());
        attribute(xml, "class", node.getClassName());
        attribute(xml, "package", node.getPackageName());
        attribute(xml, "resource-id", node.getViewIdResourceName());
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        attribute(xml, "bounds", "[" + rect.left + "," + rect.top + "]["
                + rect.right + "," + rect.bottom + "]");
        attribute(xml, "enabled", Boolean.toString(node.isEnabled()));
        attribute(xml, "visible-to-user", Boolean.toString(node.isVisibleToUser()));
        attribute(xml, "clickable", Boolean.toString(node.isClickable()));
        attribute(xml, "checked", Boolean.toString(node.isChecked()));
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) writeNode(xml, child);
        }
        xml.endTag(null, "node");
    }

    private static void attribute(XmlSerializer xml, String name, CharSequence value) throws Exception {
        xml.attribute(null, name, value == null ? "" : value.toString());
    }

    private static String regionRects(Region region) {
        StringBuilder value = new StringBuilder();
        RegionIterator iterator = new RegionIterator(region);
        Rect rect = new Rect();
        while (iterator.next(rect)) {
            if (value.length() > 0) value.append(';');
            value.append(rect.toShortString());
        }
        return value.toString();
    }

    private static String windowTypeName(int type) {
        switch (type) {
            case AccessibilityWindowInfo.TYPE_APPLICATION: return "APPLICATION";
            case AccessibilityWindowInfo.TYPE_INPUT_METHOD: return "INPUT_METHOD";
            case AccessibilityWindowInfo.TYPE_SYSTEM: return "SYSTEM";
            case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY: return "ACCESSIBILITY_OVERLAY";
            default: return "OTHER";
        }
    }
}
