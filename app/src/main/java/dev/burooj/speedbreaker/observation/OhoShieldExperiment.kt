package dev.burooj.speedbreaker.observation

// DEBUG-SB-OHO: temporary, Calculator-only input-routing experiment. Remove in full.
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import dev.burooj.speedbreaker.R

/**
 * A deliberately phone-specific experiment for placing an input sink below Samsung OHO.
 *
 * Nothing in this class is production support. Every uncertain observation restores the top
 * overlay's full touch region before the experimental shield is detached.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class OhoShieldExperiment(private val service: SpeedbreakerService) {
    private data class WindowSample(
        val id: Int,
        val type: Int,
        val layer: Int,
        val displayId: Int,
        val packageName: String?,
        val region: Region,
        val description: String?,
        val focused: Boolean,
    ) {
        val signature: String
            get() = "$id/$type/$layer/$displayId/$packageName/$focused/${region}"
        val geometrySignature: String
            get() = "$id/$type/$displayId/$packageName/${region}"
    }

    private data class Scene(
        val target: WindowSample,
        val top: WindowSample,
        val leftHandle: WindowSample,
        val allOho: List<WindowSample>,
        val requiredHole: Region,
    ) {
        val signature: String
            get() = buildString {
                append(target.signature)
                // Our intended touch hole changes top.region, not the window geometry.
                append('|').append(top.id).append('/').append(top.layer).append('/').append(top.focused)
                append('|').append(leftHandle.signature)
                append('|').append(requiredHole)
                allOho.sortedBy { it.id }.forEach { append('|').append(it.signature) }
            }
    }

    private data class Shield(
        val targetId: Int,
        val targetRegion: Region,
        val ohoBaseline: Map<Int, String>,
        val host: SurfaceControlViewHost,
        val surfacePackage: SurfaceControlViewHost.SurfacePackage,
        val surface: SurfaceControl,
        val view: ShieldView,
        val attachedElapsedMs: Long,
    )

    private class ShieldView(
        service: SpeedbreakerService,
        private val onTrace: (String) -> Unit,
    ) : View(service) {
        private var downs = 0
        private var moves = 0
        private var ups = 0
        private var start = "none"

        init {
            // This is also the only honest identifier available through the accessibility tree:
            // API 35 cannot give SurfaceControlViewHost custom WindowManager.LayoutParams.
            contentDescription = SHIELD_DESCRIPTION
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            // Force a visible surface without a perceptible result underneath the opaque SB view.
            canvas.drawColor(Color.argb(1, 0, 0, 0))
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downs++
                    moves = 0
                    ups = 0
                    start = "${event.rawX.toInt()},${event.rawY.toInt()}"
                }

                MotionEvent.ACTION_MOVE -> moves++
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ups++
                    onTrace(
                        "shield D=$downs M=$moves U=$ups start=$start " +
                            "end=${event.rawX.toInt()},${event.rawY.toInt()} " +
                            "action=${event.actionMasked}",
                    )
                }
            }
            return true
        }
    }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val notificationManager = service.getSystemService(NotificationManager::class.java)

    private var shield: Shield? = null
    private var stableSignature: String? = null
    private var stableSamples = 0
    private var holesOpen = false
    private var failedForThisOverlay = false
    private var firstUpdateMs = 0L
    private var lastFailure: String? = null
    private var lastNotificationText: String? = null
    private var lastSceneSummary = "scene=none"
    private var lastTouchTrace = "touch=none"

    fun update(top: ComposeView, value: OverlayState) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "DEBUG-SB-OHO must run on the service main thread"
        }

        if (failedForThisOverlay) return
        if (firstUpdateMs == 0L) firstUpdateMs = SystemClock.elapsedRealtime()
        if (!ensureTopIsNonModal(top)) {
            failClosed(top, "top could not become non-modal")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            failClosed(top, "requires API 34")
            return
        }
        if (value.breaker.packageName != CALCULATOR_PACKAGE) {
            failClosed(top, "target is not Calculator")
            return
        }
        if (!hasExpectedDisplay()) {
            failClosed(top, "display is not portrait 1080x2340")
            return
        }

        val sceneResult = runCatching { readScene() }
        val scene = sceneResult.getOrElse {
            val reason = it.message ?: it.javaClass.simpleName
            // Window publication and launch animation can lag addView. No hole exists yet.
            if (shield == null && !holesOpen &&
                SystemClock.elapsedRealtime() - firstUpdateMs < ATTACH_TIMEOUT_MS
            ) {
                setTopFull(top)
                publish("waiting for top window: $reason")
                return
            }
            failClosed(top, "scene rejected: $reason")
            return
        }
        lastSceneSummary = sceneSummary(scene)

        val currentShield = shield
        if (currentShield == null) {
            setTopFull(top)
            attachShield(scene, top)
            return
        }
        if (currentShield.targetId != scene.target.id ||
            currentShield.targetRegion != scene.target.region
        ) {
            failClosed(top, "target window or bounds changed")
            return
        }
        if (scene.allOho.associate { it.id to it.geometrySignature } != currentShield.ohoBaseline) {
            failClosed(top, "OHO panel/window change detected")
            return
        }

        val shieldWindow = findObservedShieldWindow()
        if (shieldWindow == null) {
            setTopFull(top)
            stableSignature = null
            stableSamples = 0
            if (SystemClock.elapsedRealtime() - currentShield.attachedElapsedMs > ATTACH_TIMEOUT_MS) {
                failClosed(top, "shield was not observable")
            } else {
                publish("waiting for observable shield")
            }
            return
        }

        if (shieldWindow.focused || !scene.top.focused) {
            failClosed(top, "shield changed focus away from top")
            return
        }
        val missingCoverage = Region(scene.requiredHole).apply {
            op(shieldWindow.region, Region.Op.DIFFERENCE)
        }
        if (!missingCoverage.isEmpty) {
            failClosed(top, "shield does not cover hole: $missingCoverage")
            return
        }
        if (!(scene.top.layer > scene.leftHandle.layer &&
                scene.leftHandle.layer > scene.target.layer &&
                shieldWindow.layer < scene.leftHandle.layer)
        ) {
            failClosed(
                top,
                "unsafe layers top=${scene.top.layer} oho=${scene.leftHandle.layer} " +
                    "shield=${shieldWindow.layer} target=${scene.target.layer}",
            )
            return
        }

        val verificationSignature = "${scene.signature}|shield=${shieldWindow.signature}"
        if (verificationSignature == stableSignature) {
            stableSamples = (stableSamples + 1).coerceAtMost(REQUIRED_STABLE_SAMPLES)
        } else {
            stableSignature = verificationSignature
            stableSamples = 1
            setTopFull(top)
        }
        if (stableSamples < REQUIRED_STABLE_SAMPLES) {
            setTopFull(top)
            publish("shield observed; stability $stableSamples/$REQUIRED_STABLE_SAMPLES")
            return
        }

        val elapsedMs = value.nowElapsedMs - value.breaker.startedElapsedMs
        if (elapsedMs < HARD_LOCK_MS) {
            setTopFull(top)
            publish("verified; hard lock")
            return
        }

        val topRegion = topTouchableRegionWithout(top, scene.requiredHole)
        if (topRegion == null) {
            failClosed(top, "top geometry no longer matches")
            return
        }
        runCatching {
            top.rootSurfaceControl?.setTouchableRegion(topRegion)
                ?: error("top root surface unavailable")
        }.onSuccess {
            holesOpen = true
            lastFailure = null
            publish("verified; left OHO + probe holes open")
        }.onFailure {
            failClosed(top, "opening holes failed: ${it.javaClass.simpleName}")
        }
    }

    fun close(top: ComposeView) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "DEBUG-SB-OHO must close on the service main thread"
        }
        val restored = setTopFull(top)
        if (restored) detachShield()
        stableSignature = null
        stableSamples = 0
        publish(if (restored) "closed" else "restore failed; shield retained")
        failedForThisOverlay = !restored
        firstUpdateMs = 0L
    }

    fun releaseAfterTopRemoved(top: ComposeView) {
        // The host calls this only after removeViewImmediate. If removal failed, keep the sink.
        if (top.isAttachedToWindow) return
        holesOpen = false
        detachShield()
        failedForThisOverlay = false
        firstUpdateMs = 0L
    }

    private fun readScene(): Scene {
        val windows = service.windows.map(::sample)
        val foreignApplications = windows.filter {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                it.packageName != service.packageName
        }
        require(foreignApplications.size == 1) {
            "foreign application windows=${foreignApplications.size}"
        }
        val target = foreignApplications.single()
        require(target.packageName == CALCULATOR_PACKAGE) { "application=${target.packageName}" }
        require(target.layer == TARGET_LAYER) { "target layer=${target.layer}" }
        require(EXPECTED_DISPLAY_BOUNDS.contains(target.region.bounds) &&
            target.region.bounds.width() == EXPECTED_DISPLAY_BOUNDS.width() &&
            target.region.bounds.height() >= 2000
        ) {
            "target region=${target.region}"
        }

        val topCandidates = windows.filter {
            it.packageName == service.packageName &&
                it.description != SHIELD_DESCRIPTION &&
                it.region.bounds == EXPECTED_CONTENT_BOUNDS
        }
        val top = requireNotNull(topCandidates.maxByOrNull { it.layer }) { "top SB window absent" }
        require(top.focused) { "top lost focus" }

        val allOho = windows.filter { it.packageName == OHO_PACKAGE }
        val leftCandidates = allOho.filter {
            it.region.bounds == EXPECTED_LEFT_HANDLE_BOUNDS && it.region.contains(1, 1500)
        }
        require(leftCandidates.size == 1) { "left OHO handles=${leftCandidates.size}" }
        val leftHandle = leftCandidates.single()
        require(top.layer > leftHandle.layer && leftHandle.layer > target.layer) { "unsafe relative layers" }

        val requiredHole = Region(leftHandle.region).apply {
            op(PROBE_STRIP, Region.Op.UNION)
        }
        val outsideTarget = Region(requiredHole).apply {
            op(target.region, Region.Op.DIFFERENCE)
        }
        require(outsideTarget.isEmpty) { "hole outside target=$outsideTarget" }

        val imeIntersects = windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                Region(it.region).apply { op(requiredHole, Region.Op.INTERSECT) }.isEmpty.not()
        }
        require(!imeIntersects) { "IME intersects hole" }

        return Scene(target, top, leftHandle, allOho, requiredHole)
    }

    private fun sample(window: AccessibilityWindowInfo): WindowSample {
        val region = Region()
        window.getRegionInScreen(region)
        val root = runCatching { window.root }.getOrNull()
        return WindowSample(
            id = window.id,
            type = window.type,
            layer = window.layer,
            displayId = window.displayId,
            packageName = root?.packageName?.toString(),
            region = region,
            description = root?.contentDescription?.toString(),
            focused = window.isFocused,
        )
    }

    private fun findObservedShieldWindow(): WindowSample? {
        val candidates = service.windows.map(::sample).filter {
            it.packageName == service.packageName && it.description == SHIELD_DESCRIPTION
        }
        return candidates.singleOrNull()
    }

    private fun attachShield(scene: Scene, top: ComposeView) {
        setTopFull(top)
        val display = service.getSystemService(DisplayManager::class.java)
            .getDisplay(scene.target.displayId)
        if (display == null) {
            failClosed(top, "default display unavailable")
            return
        }

        var host: SurfaceControlViewHost? = null
        var surfacePackage: SurfaceControlViewHost.SurfacePackage? = null
        var surface: SurfaceControl? = null
        runCatching {
            val context = service.createDisplayContext(display)
            val view = ShieldView(service) { trace ->
                lastTouchTrace = trace
                publish("shield touch received")
            }
            host = SurfaceControlViewHost(context, display, Binder())
            host!!.setView(view, scene.target.region.bounds.width(), scene.target.region.bounds.height())
            surfacePackage = host!!.surfacePackage ?: error("surface package unavailable")
            surface = surfacePackage!!.surfaceControl
            SurfaceControl.Transaction().use { transaction ->
                transaction.setVisibility(surface!!, true).setLayer(surface!!, SHIELD_CHILD_LAYER).apply()
            }
            service.attachAccessibilityOverlayToWindow(scene.target.id, surface!!)
            shield = Shield(
                targetId = scene.target.id,
                targetRegion = Region(scene.target.region),
                ohoBaseline = scene.allOho.associate { it.id to it.geometrySignature },
                host = host!!,
                surfacePackage = surfacePackage!!,
                surface = surface!!,
                view = view,
                attachedElapsedMs = SystemClock.elapsedRealtime(),
            )
            stableSignature = null
            stableSamples = 0
            publish("shield attach requested; top remains full")
        }.onFailure {
            runCatching { surface?.release() }
            runCatching { surfacePackage?.release() }
            runCatching { host?.release() }
            shield = null
            failClosed(top, "attach failed: ${it.javaClass.simpleName}")
        }
    }

    private fun topTouchableRegionWithout(top: ComposeView, screenHole: Region): Region? {
        if (top.width <= 0 || top.height <= 0) return null
        val location = IntArray(2)
        top.getLocationOnScreen(location)
        val topScreenBounds = Rect(
            location[0],
            location[1],
            location[0] + top.width,
            location[1] + top.height,
        )
        if (topScreenBounds != EXPECTED_CONTENT_BOUNDS) return null
        val localHole = Region(screenHole).apply { translate(-location[0], -location[1]) }
        return Region(0, 0, top.width, top.height).apply {
            op(localHole, Region.Op.DIFFERENCE)
        }
    }

    private fun ensureTopIsNonModal(top: ComposeView): Boolean {
        val params = top.layoutParams as? WindowManager.LayoutParams ?: return false
        if (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0) return true
        setTopFull(top)
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        return runCatching { windowManager.updateViewLayout(top, params) }
            .onFailure { lastFailure = "top non-modal update failed: ${it.javaClass.simpleName}" }
            .isSuccess
    }

    private fun setTopFull(top: ComposeView): Boolean {
        return runCatching {
            val control = top.rootSurfaceControl
            if (control == null && top.isAttachedToWindow && holesOpen) {
                error("missing root while holes are open")
            }
            control?.setTouchableRegion(null)
            holesOpen = false
            true
        }.getOrElse {
            lastFailure = "restore full failed: ${it.javaClass.simpleName}"
            false
        }
    }

    private fun failClosed(top: ComposeView, reason: String) {
        val restored = setTopFull(top)
        if (restored) detachShield()
        stableSignature = null
        stableSamples = 0
        lastFailure = reason
        failedForThisOverlay = true
        publish(if (restored) "FAILED CLOSED: $reason" else "restore failed; shield retained: $reason")
    }

    private fun detachShield() {
        val old = shield ?: return
        shield = null
        runCatching {
            SurfaceControl.Transaction().use { transaction ->
                if (old.surface.isValid) {
                    transaction.setVisibility(old.surface, false).reparent(old.surface, null).apply()
                }
            }
        }
        runCatching { old.host.release() }
        runCatching { old.surfacePackage.release() }
        runCatching { old.surface.release() }
    }

    private fun hasExpectedDisplay(): Boolean {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return service.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
            bounds == EXPECTED_DISPLAY_BOUNDS
    }

    private fun sceneSummary(scene: Scene): String =
        "target=${scene.target.id}/${scene.target.layer} ${scene.target.region}; " +
            "top=${scene.top.id}/${scene.top.layer} ${scene.top.region}; " +
            "left=${scene.leftHandle.id}/${scene.leftHandle.layer} ${scene.leftHandle.region}; " +
            "hole=${scene.requiredHole}"

    private fun publish(status: String) {
        val text = buildString {
            append(status)
            append("\nholes=").append(holesOpen)
            append(" stable=").append(stableSamples).append('/').append(REQUIRED_STABLE_SAMPLES)
            lastFailure?.let { append("\nlastFailure=").append(it) }
            append('\n').append(lastTouchTrace)
            append('\n').append(lastSceneSummary)
        }
        if (text == lastNotificationText) return
        lastNotificationText = text
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "OHO shield experiment",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(service, CHANNEL)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Speedbreaker OHO shield")
                    .setContentText(status)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setSilent(true)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .build(),
            )
        }
    }

    private companion object {
        const val CALCULATOR_PACKAGE = "com.sec.android.app.popupcalculator"
        const val OHO_PACKAGE = "com.samsung.android.sidegesturepad"
        const val SHIELD_DESCRIPTION = "DEBUG-SB-OHO Calculator shield"
        const val CHANNEL = "debug_sb_oho"
        const val NOTIFICATION_ID = 0x53424f48
        const val HARD_LOCK_MS = 8_000L
        const val ATTACH_TIMEOUT_MS = 5_000L
        const val REQUIRED_STABLE_SAMPLES = 2
        const val SHIELD_CHILD_LAYER = 1
        const val TARGET_LAYER = 0

        val EXPECTED_DISPLAY_BOUNDS = Rect(0, 0, 1080, 2340)
        val EXPECTED_CONTENT_BOUNDS = Rect(0, 94, 1080, 2340)
        val EXPECTED_LEFT_HANDLE_BOUNDS = Rect(0, 738, 60, 1898)
        val PROBE_STRIP = Region(60, 1495, 70, 1505)
    }
}
