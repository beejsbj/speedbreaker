package dev.burooj.speedbreaker.observation

// DEBUG-SB-TIMER: temporary physical-test instrumentation; remove this file in full.

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import dev.burooj.speedbreaker.R
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class TimerDiagnosticWatchdog(context: Context) : OverlayDiagnosticSink {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val mainThread = Looper.getMainLooper().thread
    private val startedAtMs = SystemClock.elapsedRealtime()
    private val running = AtomicBoolean(false)
    private val publishLock = Any()
    private val lastTickStartedMs = AtomicLong(0L)
    private val lastTickCompletedMs = AtomicLong(0L)
    private val lastOverlayPublishedMs = AtomicLong(0L)
    private val lastOverlayElapsedMs = AtomicLong(0L)
    private val phase = AtomicReference(PHASE_IDLE)
    private val renderState = AtomicReference(RenderState())
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "sb-timer-watchdog").apply { isDaemon = true }
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Timer diagnostic",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Temporary Speedbreaker timer diagnostics"
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        executor.scheduleAtFixedRate(::publish, 0L, UPDATE_MS, TimeUnit.MILLISECONDS)
    }

    fun tickStarted(nowElapsedMs: Long) {
        lastTickStartedMs.set(nowElapsedMs)
    }

    fun phase(value: String) {
        phase.set(value)
    }

    fun overlayPublished(publishedAtElapsedMs: Long, overlayElapsedMs: Long) {
        lastOverlayPublishedMs.set(publishedAtElapsedMs)
        lastOverlayElapsedMs.set(overlayElapsedMs)
    }

    fun overlayDismissed() {
        lastOverlayPublishedMs.set(0L)
        lastOverlayElapsedMs.set(0L)
        renderState.set(RenderState())
    }

    fun tickCompleted(nowElapsedMs: Long) {
        lastTickCompletedMs.set(nowElapsedMs)
        phase.set(PHASE_IDLE)
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        executor.shutdownNow()
        synchronized(publishLock) {
            manager?.cancel(NOTIFICATION_ID)
            manager?.deleteNotificationChannel(CHANNEL_ID)
        }
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val started = lastTickStartedMs.get()
        val completed = lastTickCompletedMs.get()
        val overlay = lastOverlayPublishedMs.get()
        val overlayElapsed = lastOverlayElapsedMs.get()
        val currentPhase = phase.get()
        val render = renderState.get()
        val stale = when {
            started == 0L -> now - startedAtMs > STALE_MS
            started > completed -> now - started > STALE_MS
            else -> completed == 0L || now - completed > STALE_MS
        }
        val summary = buildString {
            append("start=").append(age(now, started))
            append(" complete=").append(age(now, completed))
            append(" published=").append(age(now, overlay))
            append(" elapsed=").append(overlayElapsed).append("ms")
            append("\nphase=").append(currentPhase)
            append("\ncompose=").append(age(now, render.composeAtMs))
            append("/").append(render.composeElapsedMs).append("ms")
            append(" #").append(render.composeCount)
            append(" draw=").append(age(now, render.drawAtMs))
            append(" #").append(render.drawCount)
            append("\nview=").append(age(now, render.viewAtMs))
            append(" ").append(render.lifecycle)
            append(" a").append(render.attached.asDigit())
            append(" v").append(render.windowVisibility)
            append(" h").append(render.hardwareAccelerated.asDigit())
            append(" back=").append(render.backRegistered.asDigit())
            append("/").append(render.backInvokedCount)
            if (stale) {
                append("\nmain=").append(mainStack())
            }
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Speedbreaker timer diagnostic")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()
        synchronized(publishLock) {
            if (running.get()) {
                runCatching { manager?.notify(NOTIFICATION_ID, notification) }
            }
        }
    }

    private fun mainStack(): String = mainThread.stackTrace
        .take(STACK_DEPTH)
        .joinToString(" > ") { frame ->
            "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
        }
        .ifEmpty { "unavailable" }

    private fun age(now: Long, timestamp: Long): String =
        if (timestamp == 0L) "never" else "${(now - timestamp).coerceAtLeast(0L)}ms"

    private fun Boolean.asDigit(): Int = if (this) 1 else 0

    // DEBUG-SB-TIMER: immutable handoff; every field is captured on the main thread.
    private data class RenderState(
        val composeAtMs: Long = 0L,
        val composeElapsedMs: Long = 0L,
        val composeCount: Long = 0L,
        val drawAtMs: Long = 0L,
        val drawCount: Long = 0L,
        val viewAtMs: Long = 0L,
        val lifecycle: String = "none",
        val attached: Boolean = false,
        val windowVisibility: String = "none",
        val hardwareAccelerated: Boolean = false,
        val backRegistered: Boolean = false,
        val backInvokedCount: Long = 0L,
    )

    // DEBUG-SB-TIMER: render callbacks update atomics only; the watchdog never reads a View.
    override fun onOverlayStarted() {
        renderState.set(RenderState())
    }

    override fun onComposeSideEffect(
        nowElapsedMs: Long,
        overlayElapsedMs: Long,
        viewState: OverlayViewDiagnosticState,
    ) {
        renderState.updateAndGet { current ->
            current.copy(
                composeAtMs = nowElapsedMs,
                composeElapsedMs = overlayElapsedMs,
                composeCount = current.composeCount + 1L,
            ).withView(nowElapsedMs, viewState)
        }
    }

    override fun onDraw(nowElapsedMs: Long, viewState: OverlayViewDiagnosticState) {
        renderState.updateAndGet { current ->
            current.copy(
                drawAtMs = nowElapsedMs,
                drawCount = current.drawCount + 1L,
            ).withView(nowElapsedMs, viewState)
        }
    }

    override fun onViewState(nowElapsedMs: Long, viewState: OverlayViewDiagnosticState) {
        renderState.updateAndGet { current -> current.withView(nowElapsedMs, viewState) }
    }

    override fun onBackRegistrationChanged(registered: Boolean) {
        renderState.updateAndGet { current -> current.copy(backRegistered = registered) }
    }

    override fun onBackInvoked() {
        renderState.updateAndGet { current ->
            current.copy(backInvokedCount = current.backInvokedCount + 1L)
        }
    }

    private fun RenderState.withView(
        nowElapsedMs: Long,
        state: OverlayViewDiagnosticState,
    ): RenderState = copy(
        viewAtMs = nowElapsedMs,
        lifecycle = state.lifecycle,
        attached = state.attached,
        windowVisibility = state.windowVisibility,
        hardwareAccelerated = state.hardwareAccelerated,
    )

    companion object {
        const val PHASE_WINDOWS = "windows"
        const val PHASE_ROOT_QUERY = "root query"
        const val PHASE_APPLY = "apply"
        private const val PHASE_IDLE = "idle"
        private const val CHANNEL_ID = "debug_sb_timer"
        private const val NOTIFICATION_ID = 0x5342544d
        private const val UPDATE_MS = 1_000L
        private const val STALE_MS = 2_000L
        private const val STACK_DEPTH = 7
    }
}

// DEBUG-SB-TIMER: temporary main-thread-only input to the atomic diagnostic sink.
internal data class OverlayViewDiagnosticState(
    val lifecycle: String,
    val attached: Boolean,
    val windowVisibility: String,
    val hardwareAccelerated: Boolean,
)

// DEBUG-SB-TIMER: temporary boundary between the overlay host and background watchdog.
internal interface OverlayDiagnosticSink {
    fun onOverlayStarted()
    fun onComposeSideEffect(
        nowElapsedMs: Long,
        overlayElapsedMs: Long,
        viewState: OverlayViewDiagnosticState,
    )
    fun onDraw(nowElapsedMs: Long, viewState: OverlayViewDiagnosticState)
    fun onViewState(nowElapsedMs: Long, viewState: OverlayViewDiagnosticState)
    fun onBackRegistrationChanged(registered: Boolean)
    fun onBackInvoked()
}
