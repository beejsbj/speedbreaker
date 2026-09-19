package dev.burooj.speedbreaker.observation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.burooj.speedbreaker.R
import dev.burooj.speedbreaker.model.PauseState
import java.text.DateFormat
import java.util.Date

internal class PauseNotifications(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val shown = mutableMapOf<String, Long>()

    init {
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.pause_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = appContext.getString(R.string.pause_notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    fun reconcile(pauses: Map<String, PauseState>, nowEpochMs: Long) {
        val active = pauses.filterValues { it.untilEpochMs > nowEpochMs }
        (shown.keys - active.keys).forEach(::cancel)
        active.forEach { (packageName, pause) ->
            if (shown[packageName] != pause.untilEpochMs && show(packageName, pause, nowEpochMs)) {
                shown[packageName] = pause.untilEpochMs
            }
        }
    }

    fun isAvailable(): Boolean {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        val notificationManager = manager ?: return false
        val channel = notificationManager.getNotificationChannel(CHANNEL_ID) ?: return false
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun cancel(packageName: String) {
        manager?.cancel(notificationId(packageName))
        shown.remove(packageName)
    }

    fun cancelAll() {
        shown.keys.toList().forEach(::cancel)
    }

    private fun show(packageName: String, pause: PauseState, nowEpochMs: Long): Boolean {
        if (!isAvailable()) return false
        val label = runCatching {
            val info = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val until = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pause.untilEpochMs))
        val actionIntent = Intent(appContext, PauseEndReceiver::class.java)
            .putExtra(PauseEndReceiver.EXTRA_PACKAGE_NAME, packageName)
        val action = PendingIntent.getBroadcast(
            appContext,
            notificationId(packageName),
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val remainingMs = (pause.untilEpochMs - nowEpochMs).coerceAtLeast(1L)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(appContext.getString(R.string.pause_notification_title, label))
            .setContentText(appContext.getString(R.string.pause_notification_until, until))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setTimeoutAfter(remainingMs)
            .addAction(0, appContext.getString(R.string.pause_notification_end_now), action)
            .build()
        manager?.notify(notificationId(packageName), notification)
        return manager != null
    }

    private companion object {
        const val CHANNEL_ID = "active_pauses"

        fun notificationId(packageName: String): Int =
            0x53000000 xor (packageName.hashCode() and 0x00ffffff)
    }
}
