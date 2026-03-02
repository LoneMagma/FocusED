package com.focused.app.manager

import android.content.Context
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import com.focused.app.R
import com.focused.app.overlay.OverlayManager

/**
 * FocusBannerManager
 *
 * Shows and maintains the persistent top banner during focus mode.
 * The banner is subtle — 48dp tall, sits at the very top of the screen.
 * Shows: task name truncated + time remaining.
 * Updates the countdown every second via CountDownTimer.
 *
 * The banner does not block content — it's a reminder, not a wall.
 * The wall is shown separately by BudgetEnforcer when a monitored app opens.
 */
class FocusBannerManager(
    private val context: Context,
    private val overlayManager: OverlayManager
) {
    companion object {
        private const val OVERLAY_KEY = "focus_banner"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var countdownTimer: CountDownTimer? = null

    fun show(task: String, endsAt: Long) {
        val remainingMs = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)

        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_focus_banner, null)

            val tvTask      = view.findViewById<TextView>(R.id.tv_banner_task)
            val tvRemaining = view.findViewById<TextView>(R.id.tv_banner_remaining)

            // Truncate long tasks for the banner
            tvTask.text = if (task.length > 28) task.take(26) + "…" else task

            countdownTimer?.cancel()
            countdownTimer = object : CountDownTimer(remainingMs, 1000) {
                override fun onTick(ms: Long) {
                    val totalSec = ms / 1000
                    val min = totalSec / 60
                    val sec = totalSec % 60
                    tvRemaining.text = String.format("%d:%02d", min, sec)
                }
                override fun onFinish() {
                    tvRemaining.text = "0:00"
                }
            }.start()

            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildTopBannerParams(48))
        }
    }

    fun dismiss() {
        countdownTimer?.cancel()
        overlayManager.dismiss(OVERLAY_KEY)
    }
}
