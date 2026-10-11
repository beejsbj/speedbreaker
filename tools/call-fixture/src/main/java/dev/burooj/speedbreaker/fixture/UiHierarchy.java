package dev.burooj.speedbreaker.fixture;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;

/** One short, read-only hierarchy snapshot that leaves other Accessibility services connected. */
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
            AccessibilityNodeInfo root = null;
            long deadline = SystemClock.uptimeMillis() + 2_000;
            while (root == null && SystemClock.uptimeMillis() < deadline) {
                root = automation.getRootInActiveWindow();
                if (root == null) SystemClock.sleep(100);
            }
            if (root == null) throw new IllegalStateException("No active Accessibility window");
            File file = new File(getTargetContext().getFilesDir(), "ui-hierarchy.xml");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                XmlSerializer xml = Xml.newSerializer();
                xml.setOutput(stream, "UTF-8");
                xml.startDocument("UTF-8", true);
                xml.startTag(null, "hierarchy");
                writeNode(xml, root);
                xml.endTag(null, "hierarchy");
                xml.endDocument();
                stream.flush();
            }
            result.putString("result", "PASS");
            result.putString("file", file.getName());
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
}
