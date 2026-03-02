package com.focused.app.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import com.focused.app.R
import com.focused.app.overlay.OverlayManager
import com.focused.app.util.ActivityLogger
import com.focused.app.util.AppEvent
import com.focused.app.util.AppEventBus
import com.focused.app.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * ScrollTracker
 *
 * Listens for SCROLL_DETECTED events on AppEventBus.
 * Tracks continuous scrolling duration per app.
 *
 * If the user scrolls without a 30-second break for longer than the
 * intervention threshold (default 5 minutes), a gentle bottom-card
 * overlay appears asking if they want to stop.
 *
 * Tone: curious, not accusatory. "Still browsing?" not "Stop scrolling!"
 * The user can tap "Keep going" — no friction. We're nudging, not blocking.
 * "Take a break" dismisses the overlay and logs the intervention as accepted.
 *
 * After an intervention fires, a 10-minute cooldown prevents it firing again
 * immediately (we don't want to nag on every check).
 *
 * If focus mode is active, ScrollTracker is silent — BudgetEnforcer already
 * has the situation handled.
 */
class ScrollTracker(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val focusSessionManager: FocusSessionManager
) {
    companion object {
        private const val TAG = "ScrollTracker"
        private const val OVERLAY_KEY = "scroll_intervention"
        private const val SCROLL_TIMEOUT_MS  = 30_000L        // 30s of silence = scroll stopped
        private const val INTERVENTION_MS    = 5 * 60_000L    // 5 min continuous scroll
        private const val COOLDOWN_MS        = 10 * 60_000L   // 10 min before firing again
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Scroll start timestamps per package
    private val scrollStartedAt = mutableMapOf<String, Long>()
    // Last scroll event per package (for timeout detection)
    private val lastScrollAt    = mutableMapOf<String, Long>()
    // Timeout runnables per package
    private val stopRunnables   = mutableMapOf<String, Runnable>()
    // Last intervention time per package (cooldown)
    private val lastIntervention = mutableMapOf<String, Long>()
    // Check runnables
    private val checkRunnables  = mutableMapOf<String, Runnable>()

    private val listener: (AppEvent) -> Unit = { event ->
        if (event.type == AppEvent.Type.SCROLL_DETECTED) handleScroll(event)
        if (event.type == AppEvent.Type.APP_BACKGROUND) onAppBackground(event.packageName)
    }

    fun start() {
        AppEventBus.register(listener)
        Log.d(TAG, "ScrollTracker started")
    }

    fun stop() {
        AppEventBus.unregister(listener)
        stopRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        checkRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        stopRunnables.clear()
        checkRunnables.clear()
    }

    // -------------------------------------------------------------------------

    private fun handleScroll(event: AppEvent) {
        val pkg = event.packageName

        // Skip if focus mode is active
        if (focusSessionManager.isActive) return

        val now = System.currentTimeMillis()
        lastScrollAt[pkg] = now

        // First scroll in this session — mark start
        if (!scrollStartedAt.containsKey(pkg)) {
            scrollStartedAt[pkg] = now
            scheduleCheck(pkg)
            Log.d(TAG, "Scroll session started: $pkg")
        }

        // Reset the stop timer — user is still scrolling
        stopRunnables.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
        val stopRunnable = Runnable { onScrollStopped(pkg) }
        stopRunnables[pkg] = stopRunnable
        mainHandler.postDelayed(stopRunnable, SCROLL_TIMEOUT_MS)
    }

    private fun scheduleCheck(pkg: String) {
        checkRunnables.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
        val check = Runnable { checkIntervention(pkg) }
        checkRunnables[pkg] = check
        mainHandler.postDelayed(check, INTERVENTION_MS)
    }

    private fun checkIntervention(pkg: String) {
        val started = scrollStartedAt[pkg] ?: return
        val elapsed = System.currentTimeMillis() - started

        // Still scrolling and past threshold?
        if (elapsed < INTERVENTION_MS) return

        // In cooldown?
        val lastFired = lastIntervention[pkg] ?: 0L
        if (System.currentTimeMillis() - lastFired < COOLDOWN_MS) return

        lastIntervention[pkg] = System.currentTimeMillis()
        showIntervention(pkg, elapsed)
    }

    private fun onScrollStopped(pkg: String) {
        scrollStartedAt.remove(pkg)
        lastScrollAt.remove(pkg)
        checkRunnables.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
        Log.d(TAG, "Scroll session ended: $pkg")
    }

    private fun onAppBackground(pkg: String) {
        onScrollStopped(pkg)
        stopRunnables.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
        overlayManager.dismiss(OVERLAY_KEY)
    }

    // -------------------------------------------------------------------------

    private fun showIntervention(pkg: String, elapsedMs: Long) {
        mainHandler.post {
            val view = LayoutInflater.from(overlayManager.themedContext)
                .inflate(R.layout.overlay_scroll_intervention, null)

            val duration = DateUtil.formatMinutes(elapsedMs)
            view.findViewById<TextView>(R.id.tv_scroll_duration).text =
                "You've been scrolling for $duration."

            view.findViewById<android.widget.Button>(R.id.btn_scroll_keepgoing)
                .setOnClickListener {
                    overlayManager.dismiss(OVERLAY_KEY)
                    // Reschedule check — they chose to keep going, check again after cooldown
                    scheduleCheck(pkg)
                    Log.d(TAG, "Scroll intervention dismissed (keep going): $pkg")
                }

            view.findViewById<android.widget.Button>(R.id.btn_scroll_break)
                .setOnClickListener {
                    overlayManager.dismiss(OVERLAY_KEY)
                    ActivityLogger.log(context, ActivityLogger.SCROLL_INTERVENED, pkg,
                        "Accepted after ${DateUtil.formatDuration(elapsedMs)}")
                    onScrollStopped(pkg)
                    Log.d(TAG, "Scroll intervention accepted (take break): $pkg")
                }

            overlayManager.show(OVERLAY_KEY, view, overlayManager.buildBottomCardParams(200))
            ActivityLogger.log(context, ActivityLogger.SCROLL_INTERVENED, pkg,
                "Shown after ${DateUtil.formatDuration(elapsedMs)}")
            Log.d(TAG, "Scroll intervention shown: $pkg after $duration")
        }
    }
}
