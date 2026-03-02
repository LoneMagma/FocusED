package com.focused.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.focused.app.R
import com.focused.app.ui.MainActivity
import com.focused.app.util.NotificationHelper

/**
 * FocusedForegroundService
 *
 * Runs persistently in the background. Its sole job is to keep the process
 * alive so that FocusedAccessibilityService is never starved of resources
 * by Android's memory manager.
 *
 * The notification is minimal and non-intrusive — a single quiet line.
 * Priority is set to MIN so it sits at the bottom of the notification shade.
 */
class FocusedForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "focused_persistent"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.focused.app.START"
        const val ACTION_STOP  = "com.focused.app.STOP"

        fun start(context: Context) {
            val intent = Intent(context, FocusedForegroundService::class.java)
                .setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusedForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        // START_STICKY: if killed, Android restarts it with a null intent
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusED is active")
            .setContentText("Your limits are active.")
            .setSmallIcon(R.drawable.ic_focused_notification)  // provide a simple icon
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)      // sits quietly at the bottom
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusED",
                NotificationManager.IMPORTANCE_MIN            // no sound, no pop-up
            ).apply {
                description = "Keeps FocusED running in the background."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
