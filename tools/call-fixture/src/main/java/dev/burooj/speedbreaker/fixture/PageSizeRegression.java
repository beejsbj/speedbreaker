package dev.burooj.speedbreaker.fixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Runs inside the product process using its APK classes and native libraries. */
public final class PageSizeRegression extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        JSONObject evidence = new JSONObject();
        File directory = getTargetContext().getFilesDir();
        try {
            require(getTargetContext().getPackageName().equals("dev.burooj.speedbreaker"), "Wrong target");
            long pageSize = Os.sysconf(OsConstants._SC_PAGESIZE);
            evidence.put("page_size", pageSize).put("pid", Process.myPid())
                    .put("source_apk", getTargetContext().getApplicationInfo().sourceDir);
            require(pageSize == 16384, "Target process is not on 16 KB pages");
            ClassLoader loader = getTargetContext().getClassLoader();
            Class<?> iteratorClass = loader.loadClass("androidx.graphics.path.PathIterator");
            require(iteratorClass.getClassLoader() == loader, "Iterator not loaded from target APK");
            Class<?> conic = loader.loadClass("androidx.graphics.path.PathIterator$ConicEvaluation");
            Object asConic = conic.getField("AsConic").get(null);
            Object asQuadratics = conic.getField("AsQuadratics").get(null);
            Path path = new Path();
            path.moveTo(3.5f, 7.25f);
            path.cubicTo(11.5f, 83.25f, 91.75f, -27.5f, 143.25f, 62.5f);
            path.lineTo(21.5f, 19.25f);
            path.close();
            // An oval contains rational conics, not quadTo segments. In API 34+ the
            // public AsQuadratics route calls ConicConverter.internalConicToQuadratics
            // in the packaged native library (verified in Google's 1.0.1 sources JAR).
            // Do not instantiate PreApi34Impl on newer Skia layouts.
            path.addOval(170.5f, 8.25f, 290.5f, 91.25f, Path.Direction.CW);
            evidence.put("result", "RUNNING").put("stage", "public_iterator_native_load");
            save(directory, evidence);
            Object raw = iteratorClass.getConstructor(Path.class, conic, float.class)
                    .newInstance(path, asConic, 0.25f);
            String maps = new String(Files.readAllBytes(new File("/proc/self/maps").toPath()),
                    StandardCharsets.UTF_8);
            Files.write(new File(directory, "native-pages-maps.txt").toPath(),
                    maps.getBytes(StandardCharsets.UTF_8));
            require(maps.contains("libandroidx.graphics.path.so"), "Target native library not mapped");
            evidence.put("native_library_mapped", true);
            evidence.put("public_iterator", iterate(raw, iteratorClass, true));
            evidence.put("stage", "native_conic_conversion")
                    .put("jni_route", "PathIterator AsQuadratics -> ConicConverter.internalConicToQuadratics");
            save(directory, evidence);
            Object converted = iteratorClass.getConstructor(Path.class, conic, float.class)
                    .newInstance(path, asQuadratics, 0.25f);
            evidence.put("native_conic_iterator", iterate(converted, iteratorClass, false));
            evidence.put("stage", "complete");
            evidence.put("native_library_mapped", true).put("result", "PASS");
            result.putString("result", "PASS");
        } catch (Throwable failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
            try { evidence.put("result", "FAIL").put("failure", android.util.Log.getStackTraceString(failure)); }
            catch (Exception ignored) { }
        }
        try {
            save(directory, evidence);
        } catch (Exception failure) {
            result.putString("result", "FAIL");
            result.putString("failure", failure.toString());
        }
        finish(Activity.RESULT_OK, result); // Host requires explicit PASS and persisted evidence.
    }

    private static void save(File directory, JSONObject evidence) throws Exception {
        try (FileWriter output = new FileWriter(new File(directory, "native-pages.json"))) {
            output.write(evidence.toString(2));
        }
    }

    private static JSONObject iterate(Object iterator, Class<?> type, boolean expectConics) throws Exception {
        Method hasNext = type.getMethod("hasNext");
        Method next = type.getMethod("next", float[].class, int.class);
        float[] coordinates = new float[8];
        int segments = 0;
        int cubics = 0;
        int conics = 0;
        int quadratics = 0;
        while ((Boolean) hasNext.invoke(iterator)) {
            require(segments < 64, "Iterator did not terminate");
            Arrays.fill(coordinates, 0f);
            String segment = next.invoke(iterator, coordinates, 0).toString();
            if (segment.equals("Conic")) conics++;
            if (segment.equals("Quadratic")) quadratics++;
            if (segment.equals("Cubic")) {
                cubics++;
                require(Math.abs(coordinates[6] - 143.25f) < 0.001f
                        && Math.abs(coordinates[7] - 62.5f) < 0.001f, "Wrong cubic endpoint");
            }
            for (float coordinate : coordinates) require(Float.isFinite(coordinate), "Nonfinite coordinate");
            segments++;
        }
        require(segments > 0 && cubics > 0, "No cubic path segments");
        require(expectConics ? conics > 0 && quadratics == 0 : conics == 0 && quadratics > 0,
                "Conic path did not exercise expected native conversion");
        return new JSONObject().put("segments", segments).put("cubics", cubics)
                .put("conics", conics).put("quadratics", quadratics)
                .put("finite_coordinates", true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
