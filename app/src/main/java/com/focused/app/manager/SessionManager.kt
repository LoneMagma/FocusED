package com.focused.app.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.focused.app.data.db.FocusedDatabase
import com.focused.app.data.model.AppSession
import com.focused.app.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SessionManager
 *
 * Tracks the lifecycle of each app session. Called by BudgetEnforcer
 * when a monitored app comes to foreground or leaves it.
 *
 * Session lifecycle:
 *   openSession()  — app came to foreground
 *   pauseSession() — app left foreground; starts 15-min grace timer
 *   resumeSession()— app returned within 15 min; cancels timer, resumes
 *   closeSession() — 15-min grace expired; session permanently closed
 *
 * Returns the current session to BudgetEnforcer so it can check duration.
 */
class SessionManager(private val context: Context) {

    companion object {
        private const val TAG = "SessionManager"
        private const val GRACE_PERIOD_MS = 15 * 60 * 1000L   // 15 minutes
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    // Grace timers keyed by packageName
    private val graceTimers = mutableMapOf<String, Runnable>()

    // In-memory session id cache to avoid repeated DB reads
    private val activeSessionIds = mutableMapOf<String, Long>()

    // -------------------------------------------------------------------------

    /**
     * Called when a monitored app comes to foreground.
     * Returns session number (1, 2, 3…) so BudgetEnforcer can check the limit.
     */
    /** Returns Pair(sessionNumber, isResume) */
    suspend fun openSession(pkg: String): Pair<Int, Boolean> {
        cancelGraceTimer(pkg)

        val db = FocusedDatabase.get(context)
        val existing = db.appSessionDao().getOpenSession(pkg)

        return if (existing != null && existing.status == AppSession.STATUS_PAUSED) {
            // Resume paused session
            existing.status = AppSession.STATUS_ACTIVE
            existing.pausedAt = null
            existing.resumedAt = System.currentTimeMillis()
            db.appSessionDao().update(existing)
            activeSessionIds[pkg] = existing.id
            Log.d(TAG, "Session resumed: $pkg #${existing.sessionNumber}")
            Pair(existing.sessionNumber, true)
        } else {
            // New session — count how many closed ones exist today
            val closedToday = db.appSessionDao()
                .closedSessionsToday(pkg, DateUtil.todayKey())
            val sessionNumber = closedToday + 1

            val session = AppSession(
                packageName = pkg,
                sessionNumber = sessionNumber,
                dayKey = DateUtil.todayKey(),
                resumedAt = System.currentTimeMillis()
            )
            val id = db.appSessionDao().insert(session)
            activeSessionIds[pkg] = id
            Log.d(TAG, "Session opened: $pkg #$sessionNumber")
            Pair(sessionNumber, false)
        }
    }

    /**
     * Called when a monitored app leaves foreground.
     * Starts the 15-minute grace timer before closing the session.
     */
    fun pauseSession(pkg: String, currentDurationMs: Long) {
        scope.launch {
            val db = FocusedDatabase.get(context)
            val session = db.appSessionDao().getOpenSession(pkg) ?: return@launch
            session.status = AppSession.STATUS_PAUSED
            session.pausedAt = System.currentTimeMillis()
            session.durationMs = currentDurationMs
            db.appSessionDao().update(session)
            Log.d(TAG, "Session paused: $pkg after ${DateUtil.formatDuration(currentDurationMs)}")
        }

        val timer = Runnable {
            scope.launch { closeSession(pkg) }
        }
        graceTimers[pkg] = timer
        handler.postDelayed(timer, GRACE_PERIOD_MS)
    }

    /**
     * Sets the intention label on the current session.
     * Called after user taps an intention chip.
     */
    suspend fun setIntention(pkg: String, label: String) {
        val db = FocusedDatabase.get(context)
        val session = db.appSessionDao().getOpenSession(pkg) ?: return
        session.intentionLabel = label
        db.appSessionDao().update(session)
    }

    /**
     * Returns elapsed ms of the current active session, or 0 if none.
     */
    suspend fun currentSessionDurationMs(pkg: String): Long {
        val db = FocusedDatabase.get(context)
        val session = db.appSessionDao().getOpenSession(pkg) ?: return 0L
        if (session.status != AppSession.STATUS_ACTIVE) return session.durationMs
        return session.durationMs + (System.currentTimeMillis() - session.sessionStart)
    }


    /**
     * Total ms spent in this app today — closed/paused sessions plus current foreground time.
     * This is what BudgetEnforcer uses to enforce the daily budget.
     */
    suspend fun totalUsageTodayMs(pkg: String): Long {
        val db = FocusedDatabase.get(context)
        val historical = db.appSessionDao().totalDurationTodayMs(pkg, DateUtil.todayKey())
        // Add current active session time if app is currently open
        val active = db.appSessionDao().getOpenSession(pkg)
        // Use resumedAt (set when this active segment started) not sessionStart
        // sessionStart is the original session creation time, which includes paused gaps
        val currentMs = if (active?.status == AppSession.STATUS_ACTIVE) {
            val segmentStart = if (active.resumedAt > 0) active.resumedAt else active.sessionStart
            active.durationMs + (System.currentTimeMillis() - segmentStart)
        } else 0L
        return historical + currentMs
    }

    // -------------------------------------------------------------------------

    private suspend fun closeSession(pkg: String) {
        val db = FocusedDatabase.get(context)
        val session = db.appSessionDao().getOpenSession(pkg) ?: return
        session.status = AppSession.STATUS_CLOSED
        session.sessionEnd = System.currentTimeMillis()
        db.appSessionDao().update(session)
        activeSessionIds.remove(pkg)
        Log.d(TAG, "Session closed: $pkg #${session.sessionNumber}")
    }

    private fun cancelGraceTimer(pkg: String) {
        graceTimers.remove(pkg)?.let { handler.removeCallbacks(it) }
    }
}
