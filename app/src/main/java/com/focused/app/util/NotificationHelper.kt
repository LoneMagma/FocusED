package com.focused.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focused.app.R
import com.focused.app.ui.MainActivity

object NotificationHelper {

    private const val CHANNEL_FOCUS = "focused_focus_end"
    private const val CHANNEL_SERVICE = "focused_service"
    const val NOTIF_ID_SERVICE = 1001
    const val NOTIF_ID_FOCUS_END = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Foreground service channel — silent, persistent
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_SERVICE,
            "FocusED is running",
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = "Persistent notification while FocusED is active" })

        // Focus session end — gets user's attention
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_FOCUS,
            "Focus sessions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Notifies you when a focus session ends" })
    }

    fun notifyFocusComplete(context: Context, taskLabel: String) {
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_FOCUS)
            .setSmallIcon(R.drawable.ic_focused_notification)
            .setContentTitle("Focus session complete")
            .setContentText("You committed to: $taskLabel")
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_FOCUS_END, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silent fail
        }
    }

    fun buildServiceNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_focused_notification)
            .setContentTitle("FocusED is active")
            .setContentText("Your app limits are being enforced")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
}
