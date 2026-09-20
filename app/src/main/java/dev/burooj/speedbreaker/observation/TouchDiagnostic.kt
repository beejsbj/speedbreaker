package dev.burooj.speedbreaker.observation

// DEBUG-SB-TOUCH: temporary phone-only input routing measurement. Remove in full.
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.core.app.NotificationCompat
import dev.burooj.speedbreaker.R

internal class TouchDiagnostic(private val service: SpeedbreakerService) {
    private val manager = service.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var downs = 0
    private var moves = 0
    private var ups = 0
    private var start = "none"

    fun observe(event: MotionEvent) {
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
                val trace = "D=$downs M=$moves U=$ups start=$start end=${event.rawX.toInt()},${event.rawY.toInt()} action=${event.actionMasked}"
                handler.post { publish(trace) }
            }
        }
    }

    private fun publish(trace: String) {
        val regions = service.windows.mapNotNull { window ->
            val pkg = window.root?.packageName?.toString()
            if (pkg != service.packageName && pkg != "com.samsung.android.sidegesturepad") return@mapNotNull null
            val region = Region()
            if (Build.VERSION.SDK_INT >= 30) window.getRegionInScreen(region)
            "${if (pkg == service.packageName) "SB" else "OHO"} id=${window.id} layer=${window.layer} focus=${window.isFocused} atStart=${region.contains(1, 1500)} region=$region"
        }.joinToString("\n")
        val summary = "$trace\n$regions"
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Touch diagnostic", NotificationManager.IMPORTANCE_MIN).apply {
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        })
        runCatching {
            manager.notify(ID, NotificationCompat.Builder(service, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Speedbreaker touch diagnostic")
                .setContentText(summary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
                .setSilent(true).setOnlyAlertOnce(true).setOngoing(true).build())
        }
    }

    private companion object {
        const val CHANNEL = "debug_sb_touch"
        const val ID = 0x53425443
    }
}
